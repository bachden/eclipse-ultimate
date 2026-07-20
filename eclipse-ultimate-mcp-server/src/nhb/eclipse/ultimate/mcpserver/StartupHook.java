package nhb.eclipse.ultimate.mcpserver;

import org.eclipse.ui.IStartup;

import nhb.eclipse.ultimate.mcpserver.ui.McpStatusBarContribution;

/**
 * Runs right after the workbench finishes starting. By this point the platform,
 * workspace and Java model are available, so it is safe to boot the MCP HTTP
 * server.
 */
public class StartupHook implements IStartup {

    @Override
    public void earlyStartup() {
        Activator activator = Activator.getDefault();
        if (activator != null && McpServerPreferences.isServerEnabled()) {
            activator.startServer();
        }
        McpStatusBarContribution.install();
    }
}
