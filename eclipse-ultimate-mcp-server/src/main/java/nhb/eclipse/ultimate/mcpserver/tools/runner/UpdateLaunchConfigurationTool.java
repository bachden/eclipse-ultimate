package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Updates attributes on an existing saved launch configuration, given its name. Only the
 * attribute keys passed in {@code attributes} are changed; every other attribute already on the
 * configuration is left untouched. Use get_launch_configuration first to see current values and
 * exact attribute key names.
 */
public class UpdateLaunchConfigurationTool implements McpTool {

    @Override
    public String name() {
        return "update_launch_configuration";
    }

    @Override
    public String description() {
        return "Update attributes on an existing saved launch configuration by name. Only the given attribute "
                + "keys are changed; everything else on the configuration is preserved. Pass null for a value to "
                + "remove that attribute. See get_launch_configuration for current values and exact key names.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "name", "string", "Exact name of the launch configuration to update");
        JsonObject attributes = new JsonObject();
        attributes.addProperty("type", "object");
        attributes.addProperty("description",
                "Attribute key -> value map to merge in. Values may be strings, numbers, booleans, arrays of "
                        + "strings, or null to remove that attribute.");
        schema.getAsJsonObject("properties").add("attributes", attributes);
        return Schemas.required(schema, "name", "attributes");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String name = Schemas.requireString(arguments, "name");
        if (!arguments.has("attributes") || !arguments.get("attributes").isJsonObject()) {
            throw new IllegalArgumentException("Missing required argument: attributes");
        }
        JsonObject attributes = arguments.getAsJsonObject("attributes");

        ILaunchConfiguration config = LaunchConfigurationLookup.find(name);
        ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
        LaunchAttributes.apply(wc, attributes);
        wc.doSave();
        return "Updated launch configuration \"" + name + "\" (" + attributes.size() + " attribute(s))";
    }
}
