package nhb.eclipse.ultimate.mcpserver.server;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.mcp.ToolRegistry;

/**
 * Handles MCP JSON-RPC 2.0 methods: initialize, tools/list, tools/call, ping.
 * Returns the response object for requests, or {@code null} for notifications.
 * <p>
 * {@code tools/call} runs the tool on its own virtual thread with a hard timeout: some tools
 * ultimately block on Eclipse's workspace scheduling rules (e.g. while a build or another edit
 * holds the lock), which is a genuine, uninterruptible-by-us wait, not a bug in the tool itself.
 * Without a timeout that wait is unbounded and slowly starves every other request; with it, a
 * stuck call fails loudly instead of silently pinning a thread forever.
 */
public class McpDispatcher {

    private static final String SERVER_NAME = "eclipse-ultimate-mcp";
    private static final String SERVER_VERSION = "0.1.0";
    private static final String DEFAULT_PROTOCOL = "2024-11-05";
    private static final long TOOL_CALL_TIMEOUT_SECONDS = 60;

    private final ToolRegistry tools = new ToolRegistry();
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Releases the tool-call executor; call when the owning HTTP server stops. */
    public void shutdown() {
        toolExecutor.shutdownNow();
    }

    public JsonObject dispatch(JsonObject request) {
        JsonElement id = request.get("id");
        String method = request.has("method") ? request.get("method").getAsString() : null;

        // Notifications carry no id and expect no response.
        boolean isNotification = (id == null || id.isJsonNull());

        if (method == null) {
            return isNotification ? null : error(id, -32600, "Invalid Request: missing method");
        }

        switch (method) {
        case "initialize":
            return result(id, initialize(request));
        case "ping":
            return result(id, new JsonObject());
        case "tools/list":
            return result(id, toolsList());
        case "tools/call":
            return toolsCall(id, request);
        default:
            if (isNotification) {
                // e.g. notifications/initialized — nothing to do.
                return null;
            }
            return error(id, -32601, "Method not found: " + method);
        }
    }

    private JsonObject initialize(JsonObject request) {
        String protocol = DEFAULT_PROTOCOL;
        JsonObject params = request.getAsJsonObject("params");
        if (params != null && params.has("protocolVersion") && !params.get("protocolVersion").isJsonNull()) {
            protocol = params.get("protocolVersion").getAsString();
        }

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", protocol);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject toolsList() {
        JsonObject result = new JsonObject();
        result.add("tools", tools.listJson());
        return result;
    }

    private JsonObject toolsCall(JsonElement id, JsonObject request) {
        JsonObject params = request.getAsJsonObject("params");
        if (params == null || !params.has("name")) {
            return error(id, -32602, "Invalid params: missing tool name");
        }
        String toolName = params.get("name").getAsString();
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + toolName);
        }
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments")
                : new JsonObject();

        Callable<String> call = () -> tool.execute(arguments);
        Future<String> future = toolExecutor.submit(call);
        try {
            String text = future.get(TOOL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return result(id, toolContent(text, false));
        } catch (TimeoutException e) {
            future.cancel(true);
            return result(id, toolContent("Error: tool '" + toolName + "' timed out after "
                    + TOOL_CALL_TIMEOUT_SECONDS + "s (it may be blocked on an Eclipse workspace lock — "
                    + "e.g. a build or another edit in progress)", true));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            return result(id, toolContent("Error: " + message, true));
        }
    }

    private JsonObject toolContent(String text, boolean isError) {
        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", text);

        JsonArray content = new JsonArray();
        content.add(textBlock);

        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    private JsonObject result(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    private JsonObject error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? null : id);
        response.add("error", error);
        return response;
    }
}
