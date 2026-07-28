package nhb.eclipse.ultimate.mcpserver.ui;

import java.io.File;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Persists user-adjustable layout of the Connections window (tree column widths, the tree/side
 * panel sash ratio, window size, auto-refresh interval) across close/reopen and across Eclipse
 * restarts.
 * <p>
 * Backed by a plain {@link DialogSettings} file in the plugin's state location, rather than
 * {@code AbstractUIPlugin#getDialogSettings()} — {@link nhb.eclipse.ultimate.mcpserver.Activator}
 * is a plain {@code BundleActivator}, not a UI plugin, and switching its base class just for this
 * would be a bigger change than the setting itself warrants.
 */
final class ConnectionsUiSettings {

    private static final String FILE_NAME = "connections-window.xml";
    private static final String SECTION = "connectionsWindow";

    static final String KEY_SASH_LEFT_WEIGHT = "sashLeftWeight";
    static final String KEY_SASH_RIGHT_WEIGHT = "sashRightWeight";
    static final String KEY_WINDOW_WIDTH = "windowWidth";
    static final String KEY_WINDOW_HEIGHT = "windowHeight";
    static final String KEY_COL_PREFIX_WIDTH = "col.width.";
    static final String KEY_AUTO_REFRESH_INDEX = "autoRefreshIndex";
    static final String KEY_MAX_ENTRIES = "maxEntries";

    private static ConnectionsUiSettings instance;

    private final IDialogSettings settings;

    private ConnectionsUiSettings(IDialogSettings settings) {
        this.settings = settings;
    }

    static synchronized ConnectionsUiSettings getInstance() {
        if (instance == null) {
            instance = new ConnectionsUiSettings(load());
        }
        return instance;
    }

    IDialogSettings section() {
        return settings;
    }

    void save() {
        try {
            settings.save(settingsFilePath());
        } catch (Exception e) {
            // Best effort: losing a saved column width/sash ratio isn't worth surfacing to the user.
        }
    }

    private static IDialogSettings load() {
        DialogSettings root = new DialogSettings(SECTION);
        String path = settingsFilePath();
        if (path != null && new File(path).isFile()) {
            try {
                root.load(path);
            } catch (Exception e) {
                // Corrupt/missing file: fall back to defaults, as if this were the first run.
            }
        }
        return DialogSettings.getOrCreateSection(root, SECTION);
    }

    private static String settingsFilePath() {
        Bundle bundle = FrameworkUtil.getBundle(ConnectionsUiSettings.class);
        if (bundle == null) {
            return null;
        }
        IPath stateLocation = Platform.getStateLocation(bundle);
        return stateLocation.append(FILE_NAME).toOSString();
    }
}
