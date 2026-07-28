package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/**
 * Two-level tree for the Connections panel:
 * <ul>
 * <li>Level 1 — one {@link McpConnectionLog.Entry} per row (Time/Remote/Method/Status/Duration).</li>
 * <li>Level 2 — "Request"/"Response" {@link JsonFieldNode}s, expandable into the full JSON tree via
 * {@link JsonTreeSupport}.</li>
 * </ul>
 * Kept inline in the same tree (rather than a separate popup) so the request and response for the
 * same call can be expanded and compared side by side.
 */
final class ConnectionsTreeContentProvider implements ITreeContentProvider {

    private final List<McpConnectionLog.Entry> entries;

    ConnectionsTreeContentProvider(List<McpConnectionLog.Entry> entries) {
        this.entries = entries;
    }

    @Override
    public Object[] getElements(Object inputElement) {
        return entries.toArray();
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof McpConnectionLog.Entry entry) {
            String entryPath = entry.timestamp.toString();
            return new Object[] { JsonTreeSupport.parse(entryPath, "Request", entry.requestJson),
                    JsonTreeSupport.parse(entryPath, "Response", entry.responseJson) };
        }
        if (parentElement instanceof JsonFieldNode node) {
            return JsonTreeSupport.children(node);
        }
        return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof McpConnectionLog.Entry) {
            return true;
        }
        if (element instanceof JsonFieldNode node) {
            return JsonTreeSupport.hasChildren(node);
        }
        return false;
    }
}
