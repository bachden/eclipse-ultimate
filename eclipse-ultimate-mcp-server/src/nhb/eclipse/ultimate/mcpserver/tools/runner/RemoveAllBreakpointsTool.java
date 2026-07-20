package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Removes every breakpoint in the workspace. */
public class RemoveAllBreakpointsTool implements McpTool {

    @Override
    public String name() {
        return "remove_all_breakpoints";
    }

    @Override
    public String description() {
        return "Remove every breakpoint currently set in the workspace.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints();
        DebugPlugin.getDefault().getBreakpointManager().removeBreakpoints(breakpoints, true);
        return "Removed " + breakpoints.length + " breakpoint(s)";
    }
}
