package nhb.eclipse.ultimate.mcpserver.ui;

import com.google.gson.JsonElement;

/**
 * One row in a JSON tree view: a member/element name paired with its value node.
 * <p>
 * Deliberately a plain class (not a record): {@link org.eclipse.jface.viewers.TreeViewer} tracks
 * item identity with {@code equals()}/{@code hashCode()}, and Gson's {@link JsonElement}
 * implements structural equality — two distinct nodes at different positions in the tree (e.g.
 * two sibling fields that happen to hold the same value) would collide under a record's
 * auto-generated, value-based {@code equals()} and confuse the viewer's expand/collapse state.
 * Identity equality (the default from {@link Object}) is what we actually want here.
 */
final class JsonFieldNode {

    private final String label;
    private final JsonElement value;

    JsonFieldNode(String label, JsonElement value) {
        this.label = label;
        this.value = value;
    }

    String label() {
        return label;
    }

    JsonElement value() {
        return value;
    }
}
