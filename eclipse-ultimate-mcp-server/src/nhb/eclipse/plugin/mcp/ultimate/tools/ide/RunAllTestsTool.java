package nhb.eclipse.plugin.mcp.ultimate.tools.ide;

import com.google.gson.JsonObject;

import nhb.eclipse.plugin.mcp.ultimate.mcp.McpTool;
import nhb.eclipse.plugin.mcp.ultimate.tools.Schemas;

/** Runs every JUnit test in a project and blocks until completion, returning captured console output. */
public class RunAllTestsTool implements McpTool {

    @Override
    public String name() {
        return "run_all_tests";
    }

    @Override
    public String description() {
        return "Run every JUnit test in a project and return the captured console output. Blocks until the run "
                + "finishes or times out.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose tests to run");
        Schemas.prop(schema, "timeoutSeconds", "integer", "Max time to wait for the run to finish (default 300)");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        int timeoutSeconds = Schemas.optInt(arguments, "timeoutSeconds", 300);
        return JUnitLaunch.runProject(projectName, timeoutSeconds * 1000L);
    }
}
