package nhb.eclipse.ultimate.mcpserver.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Opens the MCP server preference page directly (menu: Eclipse Ultimate MCP >
 * Settings...).
 */
public class OpenPreferencesHandler extends AbstractHandler {

    public static final String PAGE_ID = "nhb.eclipse.ultimate.mcpserver.preferencePage";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(HandlerUtil.getActiveShell(event), PAGE_ID,
                new String[] { PAGE_ID }, null);
        if (dialog != null) {
            dialog.open();
        }
        return null;
    }
}
