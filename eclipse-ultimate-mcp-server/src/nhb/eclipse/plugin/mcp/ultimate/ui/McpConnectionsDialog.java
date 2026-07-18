package nhb.eclipse.plugin.mcp.ultimate.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import nhb.eclipse.plugin.mcp.ultimate.server.McpConnectionLog;

/** Shows the most recent client connections/requests to the MCP HTTP server. */
public class McpConnectionsDialog extends TitleAreaDialog {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final McpConnectionLog connectionLog;

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
        setMessage("Most recent client requests handled by the MCP HTTP server.");

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Table table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.widthHint = 520;
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

        List<McpConnectionLog.Entry> entries = connectionLog != null ? connectionLog.recent()
                : List.of();
        for (int i = entries.size() - 1; i >= 0; i--) {
            McpConnectionLog.Entry entry = entries.get(i);
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(0, TIME_FORMAT.format(entry.timestamp));
            item.setText(1, entry.remoteAddress);
            item.setText(2, entry.detail);
            item.setText(3, entry.success ? "OK" : "Denied");
        }

        if (entries.isEmpty()) {
            setMessage("No connections have been recorded yet.");
        }

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(600, 420);
    }
}
