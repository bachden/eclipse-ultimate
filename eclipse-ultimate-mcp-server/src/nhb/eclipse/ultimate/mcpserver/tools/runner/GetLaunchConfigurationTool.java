package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunchConfiguration;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns every attribute of a single saved launch configuration, not just the common ones. */
public class GetLaunchConfigurationTool implements McpTool {

    @Override
    public String name() {
        return "get_launch_configuration";
    }

    @Override
    public String description() {
        return "Get full details of a single saved launch configuration by name: its type id and every attribute "
                + "(project, main class, VM/program arguments, working directory, etc — whatever that launch "
                + "type stores). Use list_launch_configurations first to find the exact name.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "name", "string", "Exact name of the launch configuration");
        return Schemas.required(schema, "name");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String name = Schemas.requireString(arguments, "name");
        ILaunchConfiguration config = LaunchConfigurationLookup.find(name);

        JsonObject result = new JsonObject();
        result.addProperty("name", config.getName());
        result.addProperty("typeId", config.getType() != null ? config.getType().getIdentifier() : null);
        result.addProperty("typeName", config.getType() != null ? config.getType().getName() : null);

        JsonObject attributes = new JsonObject();
        for (var entry : config.getAttributes().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                attributes.add(entry.getKey(), null);
            } else if (value instanceof Boolean b) {
                attributes.addProperty(entry.getKey(), b);
            } else if (value instanceof Number n) {
                attributes.addProperty(entry.getKey(), n);
            } else {
                attributes.addProperty(entry.getKey(), String.valueOf(value));
            }
        }
        result.add("attributes", attributes);

        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
