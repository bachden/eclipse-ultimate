package nhb.eclipse.ultimate.mcpserver.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/** Column 0 shows the tree label (time, or "Request"/"Response", or a JSON field); columns 1-4 only apply to entry rows. */
final class ConnectionsTreeLabelProvider extends LabelProvider implements ITableLabelProvider {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public Image getColumnImage(Object element, int columnIndex) {
        return null;
    }

    @Override
    public String getColumnText(Object element, int columnIndex) {
        if (element instanceof McpConnectionLog.Entry entry) {
            return switch (columnIndex) {
            case 0 -> TIME_FORMAT.format(entry.timestamp);
            case 1 -> entry.remoteAddress;
            case 2 -> entry.detail;
            case 3 -> entry.success ? "OK" : "Denied";
            case 4 -> formatDuration(entry.durationMillis);
            default -> "";
            };
        }
        if (element instanceof JsonFieldNode node) {
            return columnIndex == 0 ? JsonTreeSupport.label(node) : "";
        }
        return "";
    }

    private String formatDuration(long durationMillis) {
        return durationMillis < 0 ? "—" : durationMillis + " ms";
    }
}
