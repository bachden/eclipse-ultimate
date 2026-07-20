package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunch;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Terminates a running or debugging launch. */
public class StopApplicationTool implements McpTool {

    @Override
    public String name() {
        return "stop_application";
    }

    @Override
    public String description() {
        return "Terminate a launch given its index from list_active_launches.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "launchIndex", "integer", "Index of the launch, from list_active_launches");
        return Schemas.required(schema, "launchIndex");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        int launchIndex = Schemas.optInt(arguments, "launchIndex", -1);
        ILaunch launch = DebugHandles.launch(launchIndex);
        if (launch.isTerminated()) {
            return "Launch " + launchIndex + " is already terminated";
        }
        launch.terminate();
        return "Terminated launch " + launchIndex;
    }
}
