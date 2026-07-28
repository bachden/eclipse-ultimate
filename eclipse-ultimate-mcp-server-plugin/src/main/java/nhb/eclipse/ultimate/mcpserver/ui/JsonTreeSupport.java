package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** Parses a raw JSON string into {@link JsonFieldNode} children, for display in a tree viewer. */
final class JsonTreeSupport {

    private JsonTreeSupport() {
    }

    /** Parses {@code rawJson} into a single root node labelled {@code rootLabel}. */
    static JsonFieldNode parse(String rootLabel, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new JsonFieldNode(rootLabel, com.google.gson.JsonNull.INSTANCE);
        }
        try {
            return new JsonFieldNode(rootLabel, JsonParser.parseString(rawJson));
        } catch (Exception e) {
            return new JsonFieldNode(rootLabel + " (unparsable JSON: " + e.getMessage() + ")",
                    new JsonPrimitive(rawJson));
        }
    }

    /** Children of a JSON node: object members or array elements, empty for scalars. */
    static Object[] children(JsonFieldNode node) {
        JsonElement value = node.value();
        List<JsonFieldNode> children = new ArrayList<>();
        if (value != null && value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                children.add(new JsonFieldNode(entry.getKey(), entry.getValue()));
            }
        } else if (value != null && value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                children.add(new JsonFieldNode("[" + i + "]", array.get(i)));
            }
        }
        return children.toArray();
    }

    static boolean hasChildren(JsonFieldNode node) {
        JsonElement value = node.value();
        return value != null && (value.isJsonObject() && value.getAsJsonObject().size() > 0
                || value.isJsonArray() && value.getAsJsonArray().size() > 0);
    }

    private static final int LABEL_VALUE_LIMIT = 150;

    private static final Gson PRETTY_PRINT = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Single-line label: {@code name: value}, or {@code name {n}}/{@code name [n]} for containers.
     * Scalar values are capped to keep the tree scannable — a full source file in one field
     * shouldn't dominate every row's width; select the leaf to see it in full in the side panel.
     */
    static String label(JsonFieldNode node) {
        JsonElement value = node.value();
        if (value == null || value.isJsonNull()) {
            return node.label() + ": null";
        }
        if (value.isJsonObject()) {
            int size = value.getAsJsonObject().size();
            return node.label() + (size == 0 ? ": {}" : " {" + size + "}");
        }
        if (value.isJsonArray()) {
            int size = value.getAsJsonArray().size();
            return node.label() + (size == 0 ? ": []" : " [" + size + "]");
        }
        return node.label() + ": " + truncate(value.getAsString());
    }

    private static String truncate(String text) {
        String flattened = text.replaceAll("\\s+", " ");
        return flattened.length() > LABEL_VALUE_LIMIT ? flattened.substring(0, LABEL_VALUE_LIMIT) + "…" : flattened;
    }

    /**
     * The node's value as plain text, unabridged — for display in a detail/side panel.
     * <p>
     * Works for any node, not just leaves: an object/array is pretty-printed as JSON so its full
     * (unabridged, unlike the label) contents can be inspected without expanding every child by
     * hand — {@link JsonElement#getAsString()} throws {@link UnsupportedOperationException} on
     * those, so they're serialised instead.
     */
    static String fullValue(JsonFieldNode node) {
        JsonElement value = node.value();
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return PRETTY_PRINT.toJson(value);
    }
}
