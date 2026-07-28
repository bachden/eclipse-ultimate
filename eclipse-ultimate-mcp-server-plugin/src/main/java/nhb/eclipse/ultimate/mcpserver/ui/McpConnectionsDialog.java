package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/**
 * Shows the most recent client connections/requests to the MCP HTTP server, with response times.
 * Each row expands inline (no separate popup) into "Request"/"Response" nodes, which expand
 * further into the full JSON tree — so the request and response for the same call, or across
 * different calls, can be compared side by side in one view. Selecting any field in the tree shows
 * its value in full, unabridged (pretty-printed JSON for objects/arrays), in a side panel fixed to
 * the right of the tree (the tree label itself is length-capped, since values like a full source
 * file would otherwise dominate it).
 * <p>
 * Modeless: a plain {@link Window} rather than a {@link org.eclipse.jface.dialogs.Dialog}, so it
 * floats alongside the Eclipse workbench without blocking it — you can keep coding in an editor
 * while this stays open, unlike an application-modal dialog. It does not stay on top of windows
 * outside Eclipse; only {@link org.eclipse.swt.SWT#ON_TOP} would do that, which is not wanted
 * here since it would also cover unrelated applications.
 */
public class McpConnectionsDialog extends Window {

    /** At most one connections window at a time; reused/raised instead of stacking duplicates. */
    private static McpConnectionsDialog current;

    private static final String EMPTY_VALUE_MESSAGE = "Select a Request/Response field in tree";

    private static final String[] COLUMN_KEYS = { "time", "remote", "method", "status", "duration" };
    private static final String[] COLUMN_TITLES = { "Time / Field", "Remote Address", "Method", "Status",
            "Response Time" };
    private static final int[] COLUMN_DEFAULT_WIDTHS = { 320, 140, 160, 80, 100 };

    private static final String[] AUTO_REFRESH_LABELS = { "Manual", "5s", "10s", "30s", "60s" };
    private static final int[] AUTO_REFRESH_SECONDS = { 0, 5, 10, 30, 60 };

    private final McpConnectionLog connectionLog;
    private final ConnectionsUiSettings uiSettings = ConnectionsUiSettings.getInstance();
    private Composite container;
    private Label messageLabel;
    private Text valuePanel;
    private SashForm sash;
    private Tree tree;
    /** Ticks {@link #populate()} on the configured interval; re-armed via {@link #scheduleAutoRefresh}. */
    private Runnable autoRefreshTick;
    private int autoRefreshSeconds;

    private McpConnectionsDialog(Shell parentShell, McpConnectionLog connectionLog) {
        super(parentShell);
        this.connectionLog = connectionLog;
        setShellStyle(SWT.CLOSE | SWT.TITLE | SWT.RESIZE | SWT.MODELESS | SWT.MIN);
        setBlockOnOpen(false);
    }

    /** Opens the connections window, or raises the existing one if already open. */
    public static void show(Shell parentShell, McpConnectionLog connectionLog) {
        if (current != null && current.getShell() != null && !current.getShell().isDisposed()) {
            current.getShell().setActive();
            current.populate();
            return;
        }
        current = new McpConnectionsDialog(parentShell, connectionLog);
        current.create();
        McpConnectionsDialog opened = current;
        opened.getShell().addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent e) {
                opened.saveLayout();
                opened.autoRefreshSeconds = 0;
                opened.autoRefreshTick = null;
                if (current == opened) {
                    current = null;
                }
            }
        });
        current.open();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("MCP Server Connections");
        // Modeless JFace Windows don't get Escape-to-close for free the way Dialog does; wire it
        // up explicitly so it behaves the same as the modal dialog this replaced.
        shell.addListener(SWT.Traverse, event -> {
            if (event.detail == SWT.TRAVERSE_ESCAPE) {
                close();
                event.detail = SWT.TRAVERSE_NONE;
                event.doit = false;
            }
        });
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite area = new Composite(parent, SWT.NONE);
        area.setLayout(new GridLayout(1, false));
        area.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label message = new Label(area, SWT.WRAP);
        message.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        container = new Composite(area, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite buttonBar = new Composite(area, SWT.NONE);
        buttonBar.setLayout(new GridLayout(3, false));
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

        Label autoRefreshLabel = new Label(buttonBar, SWT.NONE);
        autoRefreshLabel.setText("Auto-refresh:");

        Combo autoRefreshCombo = new Combo(buttonBar, SWT.READ_ONLY);
        autoRefreshCombo.setItems(AUTO_REFRESH_LABELS);
        int savedIndex = readInt(uiSettings.section(), ConnectionsUiSettings.KEY_AUTO_REFRESH_INDEX, 0);
        autoRefreshCombo.select(savedIndex >= 0 && savedIndex < AUTO_REFRESH_LABELS.length ? savedIndex : 0);
        autoRefreshCombo.addListener(SWT.Selection, e -> {
            uiSettings.section().put(ConnectionsUiSettings.KEY_AUTO_REFRESH_INDEX, autoRefreshCombo.getSelectionIndex());
            uiSettings.save();
            scheduleAutoRefresh(autoRefreshCombo.getSelectionIndex());
        });

        Button refresh = new Button(buttonBar, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.addListener(SWT.Selection, e -> populate());

        this.messageLabel = message;
        populate();
        scheduleAutoRefresh(autoRefreshCombo.getSelectionIndex());

        return area;
    }

    /**
     * Arms (or disarms, for index 0 = Manual) periodic auto-refresh. The tick reschedules itself
     * via {@link org.eclipse.swt.widgets.Display#timerExec} as long as {@link #autoRefreshSeconds}
     * still matches the interval it was armed with and the shell is alive — changing the dropdown
     * bumps {@link #autoRefreshSeconds} to a different value (or the shell closes, which zeroes
     * it), which makes the next tick a no-op instead of rescheduling.
     */
    private void scheduleAutoRefresh(int selectionIndex) {
        int seconds = selectionIndex >= 0 && selectionIndex < AUTO_REFRESH_SECONDS.length
                ? AUTO_REFRESH_SECONDS[selectionIndex]
                : 0;
        autoRefreshSeconds = seconds;
        if (seconds <= 0) {
            autoRefreshTick = null;
            return;
        }

        int armedFor = seconds;
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                Shell shell = getShell();
                if (shell == null || shell.isDisposed() || autoRefreshSeconds != armedFor
                        || autoRefreshTick != this) {
                    return;
                }
                populate();
                shell.getDisplay().timerExec(armedFor * 1000, this);
            }
        };
        autoRefreshTick = tick;
        getShell().getDisplay().timerExec(seconds * 1000, tick);
    }

    /** Rebuilds the summary label and tree from the connection log's current contents. */
    private void populate() {
        if (sash != null && !sash.isDisposed()) {
            // Capture the user's current column widths/sash ratio before the rebuild below
            // discards them (e.g. a Refresh click shouldn't reset layout the user just set).
            saveLayout();
        }
        // Rows are rebuilt from scratch below, so the previously selected/expanded items are gone;
        // capture their paths first (stable across rebuilds — see nodePath()) so the equivalent
        // rows in the new tree can be re-selected/re-expanded afterwards, and the scroll position
        // restored, instead of the view collapsing and jumping to the top on every refresh.
        List<String> selectedPath = null;
        List<String> topPath = null;
        Set<List<String>> expandedPaths = new LinkedHashSet<>();
        if (tree != null && !tree.isDisposed()) {
            TreeItem[] selection = tree.getSelection();
            if (selection.length > 0) {
                selectedPath = nodePath(selection[0]);
            }
            TreeItem topItem = tree.getTopItem();
            if (topItem != null) {
                topPath = nodePath(topItem);
            }
            collectExpandedPaths(tree.getItems(), expandedPaths);
        }
        for (Control child : container.getChildren()) {
            child.dispose();
        }

        List<McpConnectionLog.Entry> entries = connectionLog != null ? connectionLog.recent() : List.of();
        messageLabel.setText(entries.isEmpty() ? "No connections have been recorded yet."
                : "Most recent client requests handled by the MCP HTTP server. Expand a row for its "
                        + "request/response JSON; select any field to view its value in full on the right.");

        createAverageSummary(container, entries);

        sash = new SashForm(container, SWT.HORIZONTAL);
        GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true);
        sashData.widthHint = 950;
        sashData.heightHint = 360;
        sash.setLayoutData(sashData);

        TreeViewer viewer = new TreeViewer(sash, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        tree = viewer.getTree();
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        valuePanel = new Text(sash, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        valuePanel.setText(EMPTY_VALUE_MESSAGE);

        IDialogSettings settingsSection = uiSettings.section();
        int leftWeight = readInt(settingsSection, ConnectionsUiSettings.KEY_SASH_LEFT_WEIGHT, 7);
        int rightWeight = readInt(settingsSection, ConnectionsUiSettings.KEY_SASH_RIGHT_WEIGHT, 3);
        sash.setWeights(leftWeight, rightWeight);

        for (int i = 0; i < COLUMN_KEYS.length; i++) {
            TreeColumn column = new TreeColumn(tree, i == COLUMN_KEYS.length - 1 ? SWT.RIGHT : SWT.LEFT);
            column.setText(COLUMN_TITLES[i]);
            column.setWidth(
                    readInt(settingsSection, ConnectionsUiSettings.KEY_COL_PREFIX_WIDTH + COLUMN_KEYS[i],
                            COLUMN_DEFAULT_WIDTHS[i]));
        }

        // Reverse so the most recent connection is first (matches the old table's ordering).
        List<McpConnectionLog.Entry> ordered = entries.reversed();
        viewer.setContentProvider(new ConnectionsTreeContentProvider(ordered));
        viewer.setLabelProvider(new ConnectionsTreeLabelProvider());
        viewer.setInput(ordered);

        // Re-expand shortest paths first, so each level's children exist (lazily created by the
        // viewer on expand) before a deeper path is looked up under it.
        List<List<String>> expandOrder = new ArrayList<>(expandedPaths);
        expandOrder.sort(Comparator.comparingInt(List::size));
        for (List<String> path : expandOrder) {
            TreeItem item = findItemByPath(tree.getItems(), path);
            if (item != null) {
                // setExpandedState (not TreeItem.setExpanded) is what makes the viewer populate
                // this item's children immediately, so deeper paths can be found in the loop below.
                viewer.setExpandedState(item.getData(), true);
            }
        }
        if (selectedPath != null) {
            TreeItem item = findItemByPath(tree.getItems(), selectedPath);
            if (item != null) {
                // reveal=false: restoring the scroll position ourselves below, so the
                // selection must not also trigger its own auto-scroll.
                viewer.setSelection(new StructuredSelection(item.getData()), false);
            }
        }
        if (topPath != null) {
            TreeItem item = findItemByPath(tree.getItems(), topPath);
            if (item != null) {
                tree.setTopItem(item);
            }
        }

        viewer.addSelectionChangedListener(event -> {
            Object selected = ((IStructuredSelection) event.getSelection()).getFirstElement();
            if (selected instanceof JsonFieldNode node) {
                valuePanel.setText(JsonTreeSupport.fullValue(node));
            } else {
                valuePanel.setText(EMPTY_VALUE_MESSAGE);
            }
        });

        container.layout(true, true);
        container.getParent().layout(true, true);
    }

    /** Shows the average response time per remote address, across all recorded (measured) requests. */
    private void createAverageSummary(Composite parent, List<McpConnectionLog.Entry> entries) {
        Map<String, long[]> totals = new LinkedHashMap<>(); // remoteAddress -> [sumMillis, count]
        for (McpConnectionLog.Entry entry : entries) {
            if (entry.durationMillis < 0) {
                continue;
            }
            long[] agg = totals.computeIfAbsent(entry.remoteAddress, key -> new long[2]);
            agg[0] += entry.durationMillis;
            agg[1]++;
        }
        if (totals.isEmpty()) {
            return;
        }

        StringBuilder summary = new StringBuilder("Avg response time — ");
        boolean first = true;
        for (Map.Entry<String, long[]> agg : totals.entrySet()) {
            if (!first) {
                summary.append("  |  ");
            }
            first = false;
            long avg = agg.getValue()[0] / agg.getValue()[1];
            summary.append(agg.getKey()).append(": ").append(avg).append("ms (n=").append(agg.getValue()[1])
                    .append(')');
        }

        Label label = new Label(parent, SWT.NONE);
        label.setText(summary.toString());
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    @Override
    protected Point getInitialSize() {
        IDialogSettings settingsSection = uiSettings.section();
        int width = readInt(settingsSection, ConnectionsUiSettings.KEY_WINDOW_WIDTH, 1000);
        int height = readInt(settingsSection, ConnectionsUiSettings.KEY_WINDOW_HEIGHT, 560);
        return new Point(width, height);
    }

    /** Captures the current window size, sash weights and column widths for the next open. */
    private void saveLayout() {
        IDialogSettings settingsSection = uiSettings.section();
        Shell shell = getShell();
        if (shell != null && !shell.isDisposed()) {
            Point size = shell.getSize();
            settingsSection.put(ConnectionsUiSettings.KEY_WINDOW_WIDTH, size.x);
            settingsSection.put(ConnectionsUiSettings.KEY_WINDOW_HEIGHT, size.y);
        }
        if (sash != null && !sash.isDisposed()) {
            int[] weights = sash.getWeights();
            if (weights.length == 2) {
                settingsSection.put(ConnectionsUiSettings.KEY_SASH_LEFT_WEIGHT, weights[0]);
                settingsSection.put(ConnectionsUiSettings.KEY_SASH_RIGHT_WEIGHT, weights[1]);
            }
        }
        if (tree != null && !tree.isDisposed()) {
            TreeColumn[] columns = tree.getColumns();
            for (int i = 0; i < columns.length && i < COLUMN_KEYS.length; i++) {
                settingsSection.put(ConnectionsUiSettings.KEY_COL_PREFIX_WIDTH + COLUMN_KEYS[i],
                        columns[i].getWidth());
            }
        }
        uiSettings.save();
    }

    /**
     * A stable path for a tree item, from the root down to (and including) it: the entry's
     * timestamp, then each level's label ("Request"/"Response", a JSON member name, or "[i]" for
     * an array element). Rebuilding the tree creates new node instances every time, but for the
     * same underlying (immutable, append-only) log entry the structure and labels are identical,
     * so this path — unlike object identity — survives a refresh and can be used to find the
     * equivalent item again afterwards.
     */
    private static List<String> nodePath(TreeItem item) {
        List<String> path = new ArrayList<>();
        for (TreeItem current = item; current != null; current = current.getParentItem()) {
            path.add(nodeKey(current.getData()));
        }
        Collections.reverse(path);
        return path;
    }

    private static String nodeKey(Object data) {
        if (data instanceof McpConnectionLog.Entry entry) {
            return entry.timestamp.toString();
        }
        if (data instanceof JsonFieldNode node) {
            return node.label();
        }
        return String.valueOf(data);
    }

    /** Recursively collects the path of every currently expanded item, across all created items. */
    private static void collectExpandedPaths(TreeItem[] items, Set<List<String>> out) {
        for (TreeItem item : items) {
            if (item.getExpanded()) {
                out.add(nodePath(item));
                collectExpandedPaths(item.getItems(), out);
            }
        }
    }

    /** Walks down from the given top-level items to find the item matching {@code path}. */
    private static TreeItem findItemByPath(TreeItem[] items, List<String> path) {
        TreeItem[] level = items;
        TreeItem found = null;
        for (String key : path) {
            found = null;
            for (TreeItem candidate : level) {
                if (nodeKey(candidate.getData()).equals(key)) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            level = found.getItems();
        }
        return found;
    }

    private static int readInt(IDialogSettings settingsSection, String key, int fallback) {
        String value = settingsSection.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
