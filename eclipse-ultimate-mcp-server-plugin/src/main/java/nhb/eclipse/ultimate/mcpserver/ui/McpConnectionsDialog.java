package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
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
 */
public class McpConnectionsDialog extends TitleAreaDialog {

    private static final int REFRESH_ID = IDialogConstants.CLIENT_ID + 1;

    private final McpConnectionLog connectionLog;
    private Composite container;

    public McpConnectionsDialog(Shell parentShell, McpConnectionLog connectionLog) {
        super(parentShell);
        this.connectionLog = connectionLog;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("MCP Server Connections");

        Composite area = (Composite) super.createDialogArea(parent);
        container = new Composite(area, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        populate();

        return area;
    }

    /** Rebuilds the summary label and tree from the connection log's current contents. */
    private void populate() {
        for (Control child : container.getChildren()) {
            child.dispose();
        }

        List<McpConnectionLog.Entry> entries = connectionLog != null ? connectionLog.recent() : List.of();
        setMessage(entries.isEmpty() ? "No connections have been recorded yet."
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
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, REFRESH_ID, "Refresh", false);
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == REFRESH_ID) {
            populate();
            return;
        }
        super.buttonPressed(buttonId);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(1000, 520);
    }
}
