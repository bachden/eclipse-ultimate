package nhb.eclipse.ultimate.mcpserver.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/** Shows the most recent client connections/requests to the MCP HTTP server, with response times. */
public class McpConnectionsDialog extends TitleAreaDialog {

    private static final int REFRESH_ID = IDialogConstants.CLIENT_ID + 1;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

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

    /** Rebuilds the summary label and table from the connection log's current contents. */
    private void populate() {
        for (Control child : container.getChildren()) {
            child.dispose();
        }

        List<McpConnectionLog.Entry> entries = connectionLog != null ? connectionLog.recent() : List.of();
        setMessage(entries.isEmpty() ? "No connections have been recorded yet."
                : "Most recent client requests handled by the MCP HTTP server.");

        createAverageSummary(container, entries);

        Table table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.widthHint = 600;
        tableData.heightHint = 300;
        table.setLayoutData(tableData);

        TableColumn timeCol = new TableColumn(table, SWT.LEFT);
        timeCol.setText("Time");
        timeCol.setWidth(80);

        TableColumn remoteCol = new TableColumn(table, SWT.LEFT);
        remoteCol.setText("Remote Address");
        remoteCol.setWidth(150);

        TableColumn detailCol = new TableColumn(table, SWT.LEFT);
        detailCol.setText("Request");
        detailCol.setWidth(180);

        TableColumn statusCol = new TableColumn(table, SWT.LEFT);
        statusCol.setText("Status");
        statusCol.setWidth(90);

        TableColumn durationCol = new TableColumn(table, SWT.RIGHT);
        durationCol.setText("Response Time");
        durationCol.setWidth(100);

        for (int i = entries.size() - 1; i >= 0; i--) {
            McpConnectionLog.Entry entry = entries.get(i);
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(0, TIME_FORMAT.format(entry.timestamp));
            item.setText(1, entry.remoteAddress);
            item.setText(2, entry.detail);
            item.setText(3, entry.success ? "OK" : "Denied");
            item.setText(4, formatDuration(entry.durationMillis));
        }

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

    private String formatDuration(long durationMillis) {
        return durationMillis < 0 ? "—" : durationMillis + " ms";
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
        return new Point(680, 460);
    }
}
