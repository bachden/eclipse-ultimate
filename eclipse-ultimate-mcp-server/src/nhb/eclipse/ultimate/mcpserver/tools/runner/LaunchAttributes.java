package nhb.eclipse.ultimate.mcpserver.tools.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Applies a Gson {@link JsonObject} of arbitrary attributes onto a launch configuration working copy. */
final class LaunchAttributes {

    private LaunchAttributes() {
    }

    static void apply(ILaunchConfigurationWorkingCopy wc, JsonObject attributes) {
        for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                wc.removeAttribute(key);
            } else if (value.isJsonPrimitive()) {
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    wc.setAttribute(key, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    wc.setAttribute(key, primitive.getAsInt());
                } else {
                    wc.setAttribute(key, primitive.getAsString());
                }
            } else if (value.isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    list.add(element.getAsString());
                }
                wc.setAttribute(key, list);
            }
        }
    }
}
