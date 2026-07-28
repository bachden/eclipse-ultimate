package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
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
import org.eclipse.swt.widgets.Spinner;
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
    private TreeViewer viewer;
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
        buttonBar.setLayout(new GridLayout(2, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        createMaxEntriesControl(buttonBar);

        Composite rightGroup = new Composite(buttonBar, SWT.NONE);
        rightGroup.setLayout(new GridLayout(3, false));
        rightGroup.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

        Label autoRefreshLabel = new Label(rightGroup, SWT.NONE);
        autoRefreshLabel.setText("Auto-refresh:");

        Combo autoRefreshCombo = new Combo(rightGroup, SWT.READ_ONLY);
        autoRefreshCombo.setItems(AUTO_REFRESH_LABELS);
        int savedIndex = readInt(uiSettings.section(), ConnectionsUiSettings.KEY_AUTO_REFRESH_INDEX, 0);
        autoRefreshCombo.select(savedIndex >= 0 && savedIndex < AUTO_REFRESH_LABELS.length ? savedIndex : 0);
        autoRefreshCombo.addListener(SWT.Selection, e -> {
            uiSettings.section().put(ConnectionsUiSettings.KEY_AUTO_REFRESH_INDEX, autoRefreshCombo.getSelectionIndex());
            uiSettings.save();
            scheduleAutoRefresh(autoRefreshCombo.getSelectionIndex());
        });

        Button refresh = new Button(rightGroup, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.addListener(SWT.Selection, e -> populate());

        this.messageLabel = message;
        populate();
        scheduleAutoRefresh(autoRefreshCombo.getSelectionIndex());

        return area;
    }

    /**
     * "Max requests kept" spinner, left-aligned in the button bar. Persisted across
     * close/reopen/restart via {@link ConnectionsUiSettings}; changing it doesn't take effect on
     * the log until Apply is pressed (or Cancel reverts to the last applied value) — the Apply/
     * Cancel pair only appears once the spinner value differs from what's currently applied, so
     * the bar stays uncluttered until there's actually a pending change.
     */
    private void createMaxEntriesControl(Composite buttonBar) {
        Composite maxEntriesGroup = new Composite(buttonBar, SWT.NONE);
        GridLayout groupLayout = new GridLayout(4, false);
        groupLayout.marginWidth = 0;
        groupLayout.marginHeight = 0;
        maxEntriesGroup.setLayout(groupLayout);
        maxEntriesGroup.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));

        Label maxEntriesLabel = new Label(maxEntriesGroup, SWT.NONE);
        maxEntriesLabel.setText("Max requests kept:");

        int[] appliedMaxEntries = {
                readInt(uiSettings.section(), ConnectionsUiSettings.KEY_MAX_ENTRIES,
                        McpConnectionLog.DEFAULT_MAX_ENTRIES) };
        if (connectionLog != null) {
            connectionLog.setMaxEntries(appliedMaxEntries[0]);
        }

        Spinner maxEntriesSpinner = new Spinner(maxEntriesGroup, SWT.BORDER);
        maxEntriesSpinner.setMinimum(1);
        maxEntriesSpinner.setMaximum(10000);
        maxEntriesSpinner.setSelection(appliedMaxEntries[0]);

        Button apply = new Button(maxEntriesGroup, SWT.PUSH);
        apply.setText("✔"); // heavy check mark
        apply.setToolTipText("Apply");
        apply.setVisible(false);
        GridData applyData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        applyData.exclude = true;
        apply.setLayoutData(applyData);

        Button cancel = new Button(maxEntriesGroup, SWT.PUSH);
        cancel.setText("✖"); // heavy multiplication x
        cancel.setToolTipText("Cancel");
        cancel.setVisible(false);
        GridData cancelData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        cancelData.exclude = true;
        cancel.setLayoutData(cancelData);

        Runnable[] updatePendingVisibility = new Runnable[1];
        updatePendingVisibility[0] = () -> {
            boolean pending = maxEntriesSpinner.getSelection() != appliedMaxEntries[0];
            apply.setVisible(pending);
            applyData.exclude = !pending;
            cancel.setVisible(pending);
            cancelData.exclude = !pending;
            maxEntriesGroup.layout(true, true);
            buttonBar.layout(true, true);
        };
        maxEntriesSpinner.addListener(SWT.Modify, e -> updatePendingVisibility[0].run());

        apply.addListener(SWT.Selection, e -> {
            int newValue = maxEntriesSpinner.getSelection();
            appliedMaxEntries[0] = newValue;
            if (connectionLog != null) {
                connectionLog.setMaxEntries(newValue);
            }
            uiSettings.section().put(ConnectionsUiSettings.KEY_MAX_ENTRIES, newValue);
            uiSettings.save();
            updatePendingVisibility[0].run();
            populate();
        });

        cancel.addListener(SWT.Selection, e -> {
            maxEntriesSpinner.setSelection(appliedMaxEntries[0]);
            updatePendingVisibility[0].run();
        });
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
        // Rows are rebuilt from scratch below, so the previously selected/expanded elements are
        // gone; capture the old TreeViewer's state first. McpConnectionLog.Entry and JsonFieldNode
        // both define equals()/hashCode() by stable position (entry timestamp; JSON path) rather
        // than identity, so the *new* elements built below compare equal to these old ones and the
        // viewer can restore selection/expansion against them after the rebuild instead of losing
        // that state on every refresh.
        Object[] expandedElements = null;
        IStructuredSelection previousSelection = null;
        Object previousTopElement = null;
        boolean wasFocused = false;
        if (tree != null && !tree.isDisposed() && viewer != null) {
            expandedElements = viewer.getExpandedElements();
            previousSelection = (IStructuredSelection) viewer.getSelection();
            TreeItem topItem = tree.getTopItem();
            previousTopElement = topItem != null ? topItem.getData() : null;
            wasFocused = tree.isFocusControl();
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

        viewer = new TreeViewer(sash, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
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

        // Restore expansion/selection against the *new* elements: Entry/JsonFieldNode equals() is
        // position-based (see their javadoc), so the old elements captured above still match here.
        if (expandedElements != null && expandedElements.length > 0) {
            viewer.setExpandedElements(expandedElements);
        }
        if (previousSelection != null && !previousSelection.isEmpty()) {
            // reveal=false: restoring the scroll position ourselves below, so the selection must
            // not also trigger its own auto-scroll.
            viewer.setSelection(previousSelection, false);
            // setSelection() above runs before the selection-changed listener is wired up below,
            // so it doesn't fire and the value panel is left showing the empty-state message even
            // though a row is actually selected. Populate it directly from the restored selection.
            Object selected = previousSelection.getFirstElement();
            if (selected instanceof JsonFieldNode node) {
                valuePanel.setText(JsonTreeSupport.fullValue(node));
            }
        }
        if (previousTopElement != null) {
            for (TreeItem item : tree.getItems()) {
                TreeItem match = findItemForElement(item, previousTopElement);
                if (match != null) {
                    tree.setTopItem(match);
                    break;
                }
            }
        }
        if (wasFocused) {
            // The old Tree widget is disposed and this is a brand new one; without focus, SWT
            // paints its selection in the inactive (grey) style, which reads as "selection lost"
            // even though the correct row is selected underneath.
            tree.setFocus();
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
     * Searches {@code item} and its already-created descendants for one whose data equals
     * {@code element} (via {@link McpConnectionLog.Entry#equals} / {@link JsonFieldNode#equals}).
     * Only used to restore the scroll position against a row that was already visible before the
     * refresh, so — unlike expansion/selection, which the viewer itself resolves via
     * {@code setExpandedElements}/{@code setSelection} — no lazy child creation is needed here.
     */
    private static TreeItem findItemForElement(TreeItem item, Object element) {
        if (element.equals(item.getData())) {
            return item;
        }
        for (TreeItem child : item.getItems()) {
            TreeItem match = findItemForElement(child, element);
            if (match != null) {
                return match;
            }
        }
        return null;
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
