package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.Objects;

import com.google.gson.JsonElement;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;

/**
 * One row in a JSON tree view: a member/element name paired with its value node.
 * <p>
 * {@code equals()}/{@code hashCode()} are based on {@code path} — a string identifying this node's
 * position in the tree (its parent's path plus its own label), not on {@code value}. Two sibling
 * fields that happen to hold the same value must stay distinct (structural/value equality, e.g.
 * Gson's {@link JsonElement}, would wrongly collide them), but the same field re-parsed from a
 * fresh copy of the same JSON — as happens on every panel refresh, since nothing is cached — must
 * compare equal so {@link org.eclipse.jface.viewers.TreeViewer} can match it against the previous
 * tree's expanded/selected nodes and restore that state instead of losing it on rebuild.
 * <p>
 * {@code parent} (the owning {@link McpConnectionLog.Entry}, for a top-level Request/Response node,
 * or another {@code JsonFieldNode}) backs {@link ConnectionsTreeContentProvider#getParent}, which
 * JFace's {@code TreeViewer} needs to walk up from a node to re-create/expand its ancestors — e.g.
 * when restoring selection/expansion state after a refresh rebuilds the tree from scratch.
 */
final class JsonFieldNode {

    private final Object parent;
    private final String path;
    private final String label;
    private final JsonElement value;

    JsonFieldNode(Object parent, String label, JsonElement value) {
        this.parent = parent;
        this.path = parent == null ? label : parentPath(parent) + '/' + label;
        this.label = label;
        this.value = value;
    }

    private static String parentPath(Object parent) {
        if (parent instanceof McpConnectionLog.Entry entry) {
            return entry.timestamp.toString();
        }
        return ((JsonFieldNode) parent).path();
    }

    Object parent() {
        return parent;
    }

    String path() {
        return path;
    }

    String label() {
        return label;
    }

    JsonElement value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JsonFieldNode other && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
