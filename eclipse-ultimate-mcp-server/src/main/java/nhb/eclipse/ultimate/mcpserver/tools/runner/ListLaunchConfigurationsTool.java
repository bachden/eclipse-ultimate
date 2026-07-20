package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunchConfiguration;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists all saved launch configurations, with project and main class for Java applications. */
public class ListLaunchConfigurationsTool implements McpTool {

    @Override
    public String name() {
        return "list_launch_configurations";
    }

    @Override
    public String description() {
        return "List all saved launch configurations in the workspace: name, type, and for Java applications the "
                + "project and main class. Use the name with launch_configuration.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        JsonArray results = new JsonArray();
        for (ILaunchConfiguration config : DebugHandles.manager().getLaunchConfigurations()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", config.getName());
            entry.addProperty("type", config.getType() != null ? config.getType().getName() : "unknown");
            entry.addProperty("project",
                    config.getAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""));
            entry.addProperty("mainType", config
                    .getAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, ""));
            results.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
