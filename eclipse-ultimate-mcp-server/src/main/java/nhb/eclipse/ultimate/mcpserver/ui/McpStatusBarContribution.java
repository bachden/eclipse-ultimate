package nhb.eclipse.ultimate.mcpserver.ui;

import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.WorkbenchWindow;

import nhb.eclipse.ultimate.mcpserver.Activator;
import nhb.eclipse.ultimate.mcpserver.McpServerPreferences;

/**
 * Shows the MCP server's running/starting/stopped state in the workbench
 * status line. Attaches directly to each window's
 * {@link org.eclipse.ui.internal.WorkbenchWindow}'s {@link IStatusLineManager},
 * mirroring the approach used by the sibling dbeaver-mcp-server-plugin.
 * Installed from {@link nhb.eclipse.ultimate.mcpserver.StartupHook} via
 * {@link #install()}.
 */
public final class McpStatusBarContribution {

    private static final String ITEM_ID = "nhb.eclipse.ultimate.mcpserver.statusBarItem";

    private static McpStatusBarContribution instance;

    private final Activator.ServerStateListener stateListener = this::onServerStateChanged;
    private Label statusLabel;

    private McpStatusBarContribution() {
    }

    /** Idempotent; safe to call multiple times (e.g. re-entrant startup). */
    public static synchronized void install() {
        if (instance != null) {
            return;
        }
        instance = new McpStatusBarContribution();
        instance.doInstall();
    }

    private void doInstall() {
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                attach(window);
            }
            PlatformUI.getWorkbench().addWindowListener(new IWindowListener() {
                @Override
                public void windowOpened(IWorkbenchWindow window) {
                    attach(window);
                }

                @Override
                public void windowActivated(IWorkbenchWindow window) {
                }

                @Override
                public void windowDeactivated(IWorkbenchWindow window) {
                }

                @Override
                public void windowClosed(IWorkbenchWindow window) {
                }
            });
        });
    }

    private void attach(IWorkbenchWindow window) {
        if (statusLabel != null && !statusLabel.isDisposed()) {
            // Only one status line item is needed; this workbench is single-window in practice.
            return;
        }
        if (!(window instanceof WorkbenchWindow)) {
            return;
        }
        IStatusLineManager statusLine = ((WorkbenchWindow) window).getStatusLineManager();
        if (statusLine == null) {
            return;
        }
        statusLine.add(new ContributionItem(ITEM_ID) {
            @Override
            public void fill(Composite parent) {
                Composite wrapper = new Composite(parent, SWT.NONE);
                GridLayout wrapperLayout = new GridLayout(1, false);
                wrapperLayout.marginWidth = 0;
                wrapperLayout.marginHeight = 0;
                wrapper.setLayout(wrapperLayout);

                statusLabel = new Label(wrapper, SWT.NONE);
                statusLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
                statusLabel.setToolTipText("Click to manage the Eclipse Ultimate MCP HTTP server");
                statusLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseUp(MouseEvent e) {
                        showMenu(statusLabel);
                    }
                });

                Activator activator = Activator.getDefault();
                if (activator != null) {
                    activator.addServerStateListener(stateListener);
                    refresh(activator.isServerRunning());
                } else {
                    refresh(false);
                }

                statusLabel.addDisposeListener(e -> {
                    Activator a = Activator.getDefault();
                    if (a != null) {
                        a.removeServerStateListener(stateListener);
                    }
                });
            }
        });
        statusLine.update(true);
    }

    private void onServerStateChanged(boolean running) {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (statusLabel != null && !statusLabel.isDisposed()) {
                refresh(running);
            }
        });
    }

    private void refresh(boolean running) {
        Activator activator = Activator.getDefault();
        String host = running && activator != null ? activator.getBoundHost() : McpServerPreferences.getHost();
        int port = running && activator != null ? activator.getBoundPort() : McpServerPreferences.getPort();
        Display display = statusLabel.getDisplay();
        Color color = running ? display.getSystemColor(SWT.COLOR_DARK_GREEN) : display.getSystemColor(SWT.COLOR_RED);
        statusLabel.setForeground(color);
        statusLabel.setText(running ? "● MCP: running (" + host + ":" + port + ")" : "● MCP: stopped");
        statusLabel.pack();
        Composite wrapper = statusLabel.getParent();
        if (wrapper != null) {
            wrapper.pack();
            wrapper.layout();
            Composite statusLineComposite = wrapper.getParent();
            if (statusLineComposite != null) {
                statusLineComposite.layout(true, true);
            }
        }
    }

    private void showMenu(Control control) {
        Menu menu = new Menu(control);
        boolean running = Activator.getDefault() != null && Activator.getDefault().isServerRunning();

        MenuItem startItem = new MenuItem(menu, SWT.PUSH);
        startItem.setText("Start Server");
        startItem.setEnabled(!running);
        startItem.addListener(SWT.Selection, e -> runCommand("nhb.eclipse.ultimate.mcpserver.commands.startServer"));

        MenuItem stopItem = new MenuItem(menu, SWT.PUSH);
        stopItem.setText("Stop Server");
        stopItem.setEnabled(running);
        stopItem.addListener(SWT.Selection, e -> runCommand("nhb.eclipse.ultimate.mcpserver.commands.stopServer"));

        MenuItem restartItem = new MenuItem(menu, SWT.PUSH);
        restartItem.setText("Restart Server");
        restartItem.setEnabled(running);
        restartItem.addListener(SWT.Selection,
                e -> runCommand("nhb.eclipse.ultimate.mcpserver.commands.restartServer"));

        new MenuItem(menu, SWT.SEPARATOR);

        MenuItem connectionsItem = new MenuItem(menu, SWT.PUSH);
        connectionsItem.setText("Connections...");
        connectionsItem.addListener(SWT.Selection,
                e -> runCommand("nhb.eclipse.ultimate.mcpserver.commands.showConnections"));

        MenuItem settingsItem = new MenuItem(menu, SWT.PUSH);
        settingsItem.setText("Settings...");
        settingsItem.addListener(SWT.Selection,
                e -> runCommand("nhb.eclipse.ultimate.mcpserver.commands.openPreferences"));

        menu.setVisible(true);
    }

    private void runCommand(String commandId) {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return;
            }
            IHandlerService handlerService = window.getService(IHandlerService.class);
            if (handlerService != null) {
                handlerService.executeCommand(commandId, null);
            }
        } catch (Exception e) {
            System.out.println("[eclipse-ultimate-mcp] Failed to run command " + commandId + ": " + e.getMessage());
        }
    }
}
