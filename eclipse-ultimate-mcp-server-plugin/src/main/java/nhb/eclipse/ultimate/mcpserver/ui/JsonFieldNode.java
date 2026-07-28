package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.Objects;

import com.google.gson.JsonElement;

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
 */
final class JsonFieldNode {

    private final String path;
    private final String label;
    private final JsonElement value;

    JsonFieldNode(String parentPath, String label, JsonElement value) {
        this.path = parentPath == null ? label : parentPath + '/' + label;
        this.label = label;
        this.value = value;
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
