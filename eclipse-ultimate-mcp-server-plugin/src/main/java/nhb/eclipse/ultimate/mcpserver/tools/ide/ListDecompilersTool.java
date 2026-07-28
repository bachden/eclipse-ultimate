package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.Set;

import org.eclipse.core.runtime.Platform;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Lists the Java decompiler engines available through the Enhanced Class Decompiler (ECD++)
 * plugin, with guidance on which bytecode era/language features each one is suited for.
 */
public class ListDecompilersTool implements McpTool {

    private static final String DECOMPILER_BUNDLE = "io.github.nbauma109.decompiler";

    @Override
    public String name() {
        return "list_decompilers";
    }

    @Override
    public String description() {
        return "List the Java decompiler engines available for view_source, each with guidance on the kind of "
                + "bytecode (Java language version/features, or Android/Dalvik) it's best suited for, so an "
                + "engine can be picked deliberately instead of always relying on the default. Requires the "
                + "Enhanced Class Decompiler (ECD++) plugin to be installed.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        if (Platform.getBundle(DECOMPILER_BUNDLE) == null) {
            throw new IllegalStateException(
                    "The Enhanced Class Decompiler (" + DECOMPILER_BUNDLE + ") is not installed");
        }

        String defaultType = Decompiler.decompilerType();
        Set<String> types = Decompiler.availableTypes();

        JsonArray decompilers = new JsonArray();
        for (String type : types) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", type);
            entry.addProperty("default", type.equals(defaultType));
            entry.addProperty("guidance", Decompiler.describe(type));
            decompilers.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(decompilers);
    }
}
