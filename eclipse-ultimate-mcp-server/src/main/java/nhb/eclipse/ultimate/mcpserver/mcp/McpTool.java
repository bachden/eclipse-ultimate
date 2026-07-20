package nhb.eclipse.ultimate.mcpserver.mcp;

import com.google.gson.JsonObject;

/**
 * A single MCP tool. {@link #execute(JsonObject)} returns the text payload that
 * becomes the tool call's content; throwing signals a tool error to the client.
 */
public interface McpTool {

    String name();

    String description();

    /** JSON Schema describing the tool's {@code arguments} object. */
    JsonObject inputSchema();

    String execute(JsonObject arguments) throws Exception;
}
