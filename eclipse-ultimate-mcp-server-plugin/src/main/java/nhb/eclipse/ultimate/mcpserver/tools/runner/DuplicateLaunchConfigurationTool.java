package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Duplicates a saved launch configuration with all attributes intact. */
public class DuplicateLaunchConfigurationTool implements McpTool {

    @Override
    public String name() {
        return "duplicate_launch_configuration";
    }

    @Override
    public String description() {
        return "Duplicate a saved Eclipse launch configuration, preserving its launch type, attributes, mapped resources and prototype metadata.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "sourceName", "string", "Exact name of the launch configuration to duplicate");
        Schemas.prop(schema, "newName", "string", "Name for the duplicate");
        Schemas.prop(schema, "generateUniqueName", "boolean",
                "Generate a numbered unique name when newName is already used (default false)");
        return Schemas.required(schema, "sourceName", "newName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String sourceName = Schemas.requireString(arguments, "sourceName");
        String requestedName = Schemas.requireString(arguments, "newName").trim();
        boolean generateUniqueName = Schemas.optBoolean(arguments, "generateUniqueName", false);
        if (requestedName.isEmpty()) {
            throw new IllegalArgumentException("newName cannot be blank");
        }

        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        String targetName = requestedName;
        if (find(manager, targetName) != null) {
            if (!generateUniqueName) {
                throw new IllegalArgumentException("Launch configuration already exists: " + targetName);
            }
            targetName = manager.generateLaunchConfigurationName(requestedName);
        }

        ILaunchConfiguration source = LaunchConfigurationLookup.find(sourceName);
        ILaunchConfigurationWorkingCopy copy = source.copy(targetName);
        ILaunchConfiguration saved = copy.doSave();
        return "Duplicated launch configuration \"" + sourceName + "\" as \"" + saved.getName() + "\" ("
                + saved.getType().getIdentifier() + ")";
    }

    private static ILaunchConfiguration find(ILaunchManager manager, String name) throws Exception {
        for (ILaunchConfiguration configuration : manager.getLaunchConfigurations()) {
            if (configuration.getName().equals(name)) {
                return configuration;
            }
        }
        return null;
    }
}
