package nhb.eclipse.plugin.mcp.ultimate.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import nhb.eclipse.plugin.mcp.ultimate.Activator;
import nhb.eclipse.plugin.mcp.ultimate.McpServerPreferences;

/**
 * Manually starts the MCP HTTP server (menu: Eclipse Ultimate MCP > Start
 * Server).
 */
public class StartServerHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Activator activator = Activator.getDefault();
        if (activator == null) {
            return null;
        }
        McpServerPreferences.setServerEnabled(true);
        if (activator.isServerRunning()) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Eclipse Ultimate MCP Server",
                    "The MCP server is already running.");
            return null;
        }
        try {
            activator.startServer();
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Eclipse Ultimate MCP Server",
                    "MCP server started on " + McpServerPreferences.getHost() + ":" + McpServerPreferences.getPort()
                            + ".");
        } catch (RuntimeException e) {
            MessageDialog.openError(HandlerUtil.getActiveShell(event), "Eclipse Ultimate MCP Server",
                    "Failed to start MCP server: " + e.getMessage());
        }
        return null;
    }
}
