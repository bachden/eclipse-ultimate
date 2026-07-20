package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists all breakpoints, showing their location, enabled state and any condition. */
public class ListBreakpointsTool implements McpTool {

    @Override
    public String name() {
        return "list_breakpoints";
    }

    @Override
    public String description() {
        return "List all breakpoints currently set in the workspace, showing type, location, enabled state and "
                + "any condition.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        JsonArray results = new JsonArray();
        for (IBreakpoint bp : DebugPlugin.getDefault().getBreakpointManager().getBreakpoints()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", bp.isEnabled());
            entry.addProperty("marker", bp.getMarker() != null ? bp.getMarker().getResource().getFullPath().toString() : null);
            if (bp instanceof IJavaLineBreakpoint) {
                IJavaLineBreakpoint lineBp = (IJavaLineBreakpoint) bp;
                entry.addProperty("type", "line");
                entry.addProperty("typeName", lineBp.getTypeName());
                entry.addProperty("line", lineBp.getLineNumber());
                entry.addProperty("condition", lineBp.getCondition());
            } else {
                entry.addProperty("type", bp.getModelIdentifier());
            }
            results.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
