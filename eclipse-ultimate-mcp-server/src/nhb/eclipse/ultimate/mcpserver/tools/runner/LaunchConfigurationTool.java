package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Launches a saved launch configuration by name, in run or debug mode. */
public class LaunchConfigurationTool implements McpTool {

    @Override
    public String name() {
        return "launch_configuration";
    }

    @Override
    public String description() {
        return "Launch a saved launch configuration by name (see list_launch_configurations), in run or debug "
                + "mode. Returns immediately after the launch is scheduled — poll list_active_launches for status.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "name", "string", "Exact name of the launch configuration");
        Schemas.prop(schema, "mode", "string", "'run' or 'debug' (default 'run')");
        return Schemas.required(schema, "name");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String name = Schemas.requireString(arguments, "name");
        String mode = Schemas.optString(arguments, "mode", "run").toLowerCase();
        String launchMode = "debug".equals(mode) ? ILaunchManager.DEBUG_MODE : ILaunchManager.RUN_MODE;

        ILaunchConfiguration config = LaunchConfigurationLookup.find(name);

        ILaunch launch = config.launch(launchMode, null);
        int index = java.util.Arrays.asList(DebugHandles.manager().getLaunches()).indexOf(launch);
        return "Launched " + name + " (" + mode + ") as launch index " + index;
    }
}
