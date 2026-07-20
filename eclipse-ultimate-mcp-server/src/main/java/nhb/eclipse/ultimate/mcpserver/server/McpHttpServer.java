package nhb.eclipse.ultimate.mcpserver.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal MCP "Streamable HTTP" endpoint at POST /mcp. Requests get a single
 * JSON-RPC response (application/json); notifications get 202 Accepted.
 * Host/port are supplied by the caller (see
 * {@link nhb.eclipse.ultimate.mcpserver.McpServerPreferences}). Optionally
 * requires a bearer token (see {@link #setAuth(BooleanSupplier, Supplier)})
 * and always records recent client connections for display in the UI.
 */
public class McpHttpServer {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8733;
    private static final String PATH = "/mcp";

    private final Gson gson = new Gson();
    private final McpDispatcher dispatcher = new McpDispatcher();
    private final McpConnectionLog connectionLog = new McpConnectionLog();
    private HttpServer httpServer;
    private volatile String boundHost;
    private volatile int boundPort;
    private volatile BooleanSupplier authEnabled = () -> false;
    private volatile Supplier<String> authToken = () -> null;

    /** Wires in the auth predicate/token source; called before {@link #start}. */
    public void setAuth(BooleanSupplier authEnabled, Supplier<String> authToken) {
        this.authEnabled = authEnabled != null ? authEnabled : () -> false;
        this.authToken = authToken != null ? authToken : () -> null;
    }

    public McpConnectionLog getConnectionLog() {
        return connectionLog;
    }

    public synchronized void start(String host, int port) {
        if (httpServer != null) {
            return;
        }
        try {
            httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            httpServer.createContext(PATH, new McpHandler());
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();
            boundHost = host;
            // Re-read the actual bound port: when port == 0 (random mode) the OS
            // picks one, and httpServer.getAddress() is the only place to learn it.
            boundPort = httpServer.getAddress().getPort();
            log("MCP server listening on http://" + host + ":" + boundPort + PATH);
        } catch (IOException e) {
            httpServer = null;
            log("Failed to start MCP server on " + host + ":" + port + ": " + e.getMessage());
            throw new RuntimeException("Failed to start MCP server on " + host + ":" + port, e);
        }
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            log("MCP server stopped");
        }
    }

    public synchronized boolean isRunning() {
        return httpServer != null;
    }

    public String getBoundHost() {
        return boundHost;
    }

    public int getBoundPort() {
        return boundPort;
    }

    private final class McpHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String remote = exchange.getRemoteAddress() != null
                    ? exchange.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            try {
                String method = exchange.getRequestMethod();
                if ("POST".equalsIgnoreCase(method)) {
                    if (!checkAuth(exchange, remote)) {
                        return;
                    }
                    handlePost(exchange, remote);
                } else if ("GET".equalsIgnoreCase(method)) {
                    // Server-initiated SSE stream not supported; clients fall back to POST.
                    exchange.sendResponseHeaders(405, -1);
                } else if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().add("Allow", "POST, OPTIONS");
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                log("Handler error: " + e.getMessage());
                safeError(exchange, 500, "Internal error");
            } finally {
                exchange.close();
            }
        }

        private boolean checkAuth(HttpExchange exchange, String remote) throws IOException {
            if (!authEnabled.getAsBoolean()) {
                return true;
            }
            String expected = authToken.get();
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            String presented = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? header.substring(7).trim()
                    : null;
            if (expected != null && expected.equals(presented)) {
                return true;
            }
            connectionLog.record(remote, "unauthorized", false);
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            safeError(exchange, 401, "Unauthorized");
            return false;
        }

        private void handlePost(HttpExchange exchange, String remote) throws IOException {
            String body = readBody(exchange.getRequestBody());
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(body);
            } catch (Exception e) {
                connectionLog.record(remote, "parse error", false);
                writeJson(exchange, 400, parseErrorResponse());
                return;
            }

            if (parsed.isJsonArray()) {
                // JSON-RPC batch.
                JsonArray responses = new JsonArray();
                for (JsonElement element : parsed.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        JsonObject requestObj = element.getAsJsonObject();
                        long start = System.nanoTime();
                        JsonObject response = dispatcher.dispatch(requestObj);
                        long durationMillis = (System.nanoTime() - start) / 1_000_000;
                        connectionLog.record(remote, describe(requestObj), true, durationMillis);
                        if (response != null) {
                            responses.add(response);
                        }
                    }
                }
                if (responses.isEmpty()) {
                    exchange.sendResponseHeaders(202, -1);
                } else {
                    writeJson(exchange, 200, responses);
                }
                return;
            }

            if (!parsed.isJsonObject()) {
                connectionLog.record(remote, "parse error", false);
                writeJson(exchange, 400, parseErrorResponse());
                return;
            }

            JsonObject requestObj = parsed.getAsJsonObject();
            long start = System.nanoTime();
            JsonObject response = dispatcher.dispatch(requestObj);
            long durationMillis = (System.nanoTime() - start) / 1_000_000;
            connectionLog.record(remote, describe(requestObj), true, durationMillis);
            if (response == null) {
                // Notification — acknowledge with no content.
                exchange.sendResponseHeaders(202, -1);
            } else {
                writeJson(exchange, 200, response);
            }
        }

        private String describe(JsonObject requestObj) {
            JsonElement methodEl = requestObj.get("method");
            if (methodEl == null || !methodEl.isJsonPrimitive()) {
                return "request";
            }
            String method = methodEl.getAsString();
            if ("tools/call".equals(method)) {
                JsonElement params = requestObj.get("params");
                if (params != null && params.isJsonObject()) {
                    JsonElement nameEl = params.getAsJsonObject().get("name");
                    if (nameEl != null && nameEl.isJsonPrimitive()) {
                        return "tools/call " + nameEl.getAsString();
                    }
                }
            }
            return method;
        }
    }

    private JsonObject parseErrorResponse() {
        JsonObject error = new JsonObject();
        error.addProperty("code", -32700);
        error.addProperty("message", "Parse error");
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", null);
        response.add("error", error);
        return response;
    }

    private void writeJson(HttpExchange exchange, int status, JsonElement payload) throws IOException {
        byte[] bytes = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void safeError(HttpExchange exchange, int status, String message) {
        try {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void log(String message) {
        System.out.println("[eclipse-ultimate-mcp] " + message);
    }
}
