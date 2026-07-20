package nhb.eclipse.ultimate.mcpserver.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import nhb.eclipse.ultimate.mcpserver.Activator;
import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/** Opens a dialog listing recent client connections to the MCP HTTP server. */
public class ShowConnectionsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Activator activator = Activator.getDefault();
        McpConnectionLog log = activator != null ? activator.getConnectionLog() : null;
        if (log == null) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Eclipse Ultimate MCP Server",
                    "The MCP server is not running, so there are no connections to show.");
            return null;
        }
        new McpConnectionsDialog(HandlerUtil.getActiveShell(event), log).open();
        return null;
    }
}
