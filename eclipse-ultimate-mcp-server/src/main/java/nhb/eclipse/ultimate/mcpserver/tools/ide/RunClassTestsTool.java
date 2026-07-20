package nhb.eclipse.ultimate.mcpserver.tools.ide;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Runs every test method in a single JUnit test class and blocks until
 * completion.
 */
public class RunClassTestsTool implements McpTool {

    @Override
    public String name() {
        return "run_class_tests";
    }

    @Override
    public String description() {
        return "Run every test method in a single JUnit test class with the JUnit 3/4/5/6 runner detected "
                + "by Eclipse JDT, and return the captured console output. Blocks until the run finishes or times out.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project the test class belongs to");
        Schemas.prop(schema, "fqTestClassName", "string", "Fully-qualified test class name, e.g. com.example.FooTest");
        Schemas.prop(schema, "timeoutSeconds", "integer", "Max time to wait for the run to finish (default 120)");
        return Schemas.required(schema, "projectName", "fqTestClassName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqTestClassName = Schemas.requireString(arguments, "fqTestClassName");
        int timeoutSeconds = Schemas.optInt(arguments, "timeoutSeconds", 120);
        return JUnitLaunch.runClass(projectName, fqTestClassName, timeoutSeconds * 1000L);
    }
}
