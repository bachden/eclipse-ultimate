package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Sets (or updates) a condition on an existing Java line breakpoint. */
public class SetConditionalBreakpointTool implements McpTool {

    @Override
    public String name() {
        return "set_conditional_breakpoint";
    }

    @Override
    public String description() {
        return "Set or update the condition on an existing Java line breakpoint at the given type + line. The "
                + "breakpoint must already exist (see toggle_breakpoint).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "fqName", "string", "Fully-qualified type name the breakpoint is set on");
        Schemas.prop(schema, "line", "integer", "1-based line number of the breakpoint");
        Schemas.prop(schema, "condition", "string", "Java boolean expression; breakpoint fires when true");
        return Schemas.required(schema, "fqName", "line", "condition");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String fqName = Schemas.requireString(arguments, "fqName");
        int line = Schemas.optInt(arguments, "line", -1);
        String condition = Schemas.requireString(arguments, "condition");

        for (org.eclipse.debug.core.model.IBreakpoint bp : org.eclipse.debug.core.DebugPlugin.getDefault()
                .getBreakpointManager().getBreakpoints()) {
            if (bp instanceof IJavaLineBreakpoint) {
                IJavaLineBreakpoint lineBp = (IJavaLineBreakpoint) bp;
                if (fqName.equals(lineBp.getTypeName()) && lineBp.getLineNumber() == line) {
                    lineBp.setCondition(condition);
                    lineBp.setConditionEnabled(true);
                    return "Set condition on " + fqName + ":" + line;
                }
            }
        }
        throw new IllegalArgumentException("No breakpoint found at " + fqName + ":" + line + "; create one first with toggle_breakpoint");
    }
}
