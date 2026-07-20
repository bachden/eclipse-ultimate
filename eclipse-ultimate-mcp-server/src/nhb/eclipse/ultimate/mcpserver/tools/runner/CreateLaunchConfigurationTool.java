package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Creates a new saved launch configuration of any type, by setting arbitrary attributes on it.
 * This is the generic escape hatch behind list/get/launch: any launch type Eclipse knows about
 * (Java Application, JUnit, Maven Build, Program/External Tools, Eclipse Application, ...) can be
 * created by passing its type id and the attribute keys that type expects.
 *
 * <p>Common type ids and their key attributes:
 * <ul>
 *   <li>{@code org.eclipse.jdt.launching.localJavaApplication} (Java Application) —
 *       {@code org.eclipse.jdt.launching.PROJECT_ATTR} (string),
 *       {@code org.eclipse.jdt.launching.MAIN_TYPE} (string),
 *       {@code org.eclipse.jdt.launching.VM_ARGUMENTS} (string),
 *       {@code org.eclipse.jdt.launching.PROGRAM_ARGUMENTS} (string)</li>
 *   <li>{@code org.eclipse.jdt.junit.launchconfig} (JUnit) —
 *       {@code org.eclipse.jdt.launching.PROJECT_ATTR}, {@code org.eclipse.jdt.launching.MAIN_TYPE}
 *       (fully-qualified test class), {@code org.eclipse.jdt.junit.TESTKIND}
 *       ({@code org.eclipse.jdt.junit.loader.junit4})</li>
 *   <li>{@code org.eclipse.ui.externaltools.ProgramLaunchConfigurationType} (Program) —
 *       {@code org.eclipse.ui.externaltools.ATTR_LOCATION} (absolute path to the executable),
 *       {@code org.eclipse.ui.externaltools.ATTR_TOOL_ARGUMENTS} (string),
 *       {@code org.eclipse.ui.externaltools.ATTR_WORKING_DIRECTORY} (string)</li>
 * </ul>
 * Use get_launch_configuration on a similar existing config to see the exact attribute keys and
 * value types a given type actually uses before creating a new one.
 */
public class CreateLaunchConfigurationTool implements McpTool {

    @Override
    public String name() {
        return "create_launch_configuration";
    }

    @Override
    public String description() {
        return "Create a new saved launch configuration of any type by id (Java Application, JUnit, Maven Build, "
                + "Program/External Tools, Eclipse Application, ...), setting arbitrary attributes on it. See "
                + "get_launch_configuration on a similar existing config to learn the attribute keys/types a given "
                + "launch type actually uses.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "typeId", "string",
                "Launch configuration type id, e.g. org.eclipse.jdt.launching.localJavaApplication, "
                        + "org.eclipse.jdt.junit.launchconfig, org.eclipse.ui.externaltools.ProgramLaunchConfigurationType");
        Schemas.prop(schema, "name", "string", "Name for the new launch configuration");
        JsonObject attributes = new JsonObject();
        attributes.addProperty("type", "object");
        attributes.addProperty("description",
                "Attribute key -> value map. Values may be strings, numbers, booleans, or arrays of strings.");
        schema.getAsJsonObject("properties").add("attributes", attributes);
        return Schemas.required(schema, "typeId", "name");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String typeId = Schemas.requireString(arguments, "typeId");
        String name = Schemas.requireString(arguments, "name");
        JsonObject attributes = arguments.has("attributes") && arguments.get("attributes").isJsonObject()
                ? arguments.getAsJsonObject("attributes")
                : new JsonObject();

        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = manager.getLaunchConfigurationType(typeId);
        if (type == null) {
            throw new IllegalArgumentException("Unknown launch configuration type id: " + typeId);
        }

        ILaunchConfigurationWorkingCopy wc = type.newInstance(null, manager.generateLaunchConfigurationName(name));
        LaunchAttributes.apply(wc, attributes);
        ILaunchConfiguration saved = wc.doSave();
        return "Created launch configuration \"" + saved.getName() + "\" (" + typeId + ")";
    }
}
