package nhb.eclipse.plugin.mcp.ultimate.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.plugin.mcp.ultimate.tools.coder.CoderTools;
import nhb.eclipse.plugin.mcp.ultimate.tools.git.GitTools;
import nhb.eclipse.plugin.mcp.ultimate.tools.ide.IdeTools;
import nhb.eclipse.plugin.mcp.ultimate.tools.runner.RunnerTools;

/** Registry of all MCP tools exposed by the server, grouped by origin. */
public final class ToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        IdeTools.registerAll(this::register);
        CoderTools.registerAll(this::register);
        GitTools.registerAll(this::register);
        RunnerTools.registerAll(this::register);
    }

    private void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public McpTool get(String name) {
        return tools.get(name);
    }

    /** Build the {@code tools} array for a {@code tools/list} response. */
    public JsonArray listJson() {
        JsonArray array = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            array.add(entry);
        }
        return array;
    }
}
