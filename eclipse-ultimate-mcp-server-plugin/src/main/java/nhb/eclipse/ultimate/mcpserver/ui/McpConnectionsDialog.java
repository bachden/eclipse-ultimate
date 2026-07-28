package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/**
 * Shows the most recent client connections/requests to the MCP HTTP server, with response times.
 * Each row expands inline (no separate popup) into "Request"/"Response" nodes, which expand
 * further into the full JSON tree — so the request and response for the same call, or across
 * different calls, can be compared side by side in one view.
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

    private final McpConnectionLog connectionLog;
    private Composite container;
    private Label messageLabel;

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
        current.getShell().addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent e) {
                current = null;
            }
        });
        current.open();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("MCP Server Connections");
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
        buttonBar.setLayout(new GridLayout(1, false));
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        Button refresh = new Button(buttonBar, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.addListener(SWT.Selection, e -> populate());

        this.messageLabel = message;
        populate();

        return area;
    }

    /** Rebuilds the summary label and tree from the connection log's current contents. */
    private void populate() {
        for (Control child : container.getChildren()) {
            child.dispose();
        }

        List<McpConnectionLog.Entry> entries = connectionLog != null ? connectionLog.recent() : List.of();
        messageLabel.setText(entries.isEmpty() ? "No connections have been recorded yet."
                : "Most recent client requests handled by the MCP HTTP server. Expand a row to inspect its "
                        + "request/response JSON.");

        createAverageSummary(container, entries);

        TreeViewer viewer = new TreeViewer(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        Tree tree = viewer.getTree();
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);
        GridData treeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        treeData.widthHint = 950;
        treeData.heightHint = 360;
        tree.setLayoutData(treeData);

        TreeColumn timeCol = new TreeColumn(tree, SWT.LEFT);
        timeCol.setText("Time / Field");
        timeCol.setWidth(320);

        TreeColumn remoteCol = new TreeColumn(tree, SWT.LEFT);
        remoteCol.setText("Remote Address");
        remoteCol.setWidth(140);

        TreeColumn methodCol = new TreeColumn(tree, SWT.LEFT);
        methodCol.setText("Method");
        methodCol.setWidth(160);

        TreeColumn statusCol = new TreeColumn(tree, SWT.LEFT);
        statusCol.setText("Status");
        statusCol.setWidth(80);

        TreeColumn durationCol = new TreeColumn(tree, SWT.RIGHT);
        durationCol.setText("Response Time");
        durationCol.setWidth(100);

        // Reverse so the most recent connection is first (matches the old table's ordering).
        List<McpConnectionLog.Entry> ordered = entries.reversed();
        viewer.setContentProvider(new ConnectionsTreeContentProvider(ordered));
        viewer.setLabelProvider(new ConnectionsTreeLabelProvider());
        viewer.setInput(ordered);

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
        return new Point(1000, 560);
    }
}
