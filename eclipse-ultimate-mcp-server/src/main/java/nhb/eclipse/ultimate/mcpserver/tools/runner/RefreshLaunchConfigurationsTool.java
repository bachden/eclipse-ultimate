package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Forces the workspace to notice launch configuration files written directly to disk (e.g. by an
 * external process, or a tool other than create/update_launch_configuration). The launch manager
 * discovers new/changed {@code .launch} files through the platform's resource-change
 * notifications, which only fire after a filesystem refresh — this triggers that refresh and
 * reports how many configurations are visible afterwards.
 */
public class RefreshLaunchConfigurationsTool implements McpTool {

    @Override
    public String name() {
        return "refresh_launch_configurations";
    }

    @Override
    public String description() {
        return "Force the workspace to rescan for launch configuration changes made directly on disk (outside "
                + "create_launch_configuration/update_launch_configuration), then return the up-to-date count and "
                + "names. Use this if a .launch file was added/edited by another process and isn't showing up yet.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());

        ILaunchConfiguration[] configs = DebugHandles.manager().getLaunchConfigurations();
        StringBuilder sb = new StringBuilder();
        sb.append("Refreshed. ").append(configs.length).append(" launch configuration(s) visible:\n");
        for (ILaunchConfiguration config : configs) {
            sb.append("- ").append(config.getName()).append('\n');
        }
        return sb.toString();
    }
}
