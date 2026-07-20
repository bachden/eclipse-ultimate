package nhb.eclipse.ultimate.mcpserver.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import nhb.eclipse.ultimate.mcpserver.Activator;
import nhb.eclipse.ultimate.mcpserver.McpServerPreferences;

/**
 * Manually stops the MCP HTTP server (menu: Eclipse Ultimate MCP > Stop
 * Server).
 */
public class StopServerHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Activator activator = Activator.getDefault();
        if (activator == null) {
            return null;
        }
        McpServerPreferences.setServerEnabled(false);
        if (!activator.isServerRunning()) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Eclipse Ultimate MCP Server",
                    "The MCP server is not running.");
            return null;
        }
        activator.stopServer();
        MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Eclipse Ultimate MCP Server",
                "MCP server stopped.");
        return null;
    }
}
