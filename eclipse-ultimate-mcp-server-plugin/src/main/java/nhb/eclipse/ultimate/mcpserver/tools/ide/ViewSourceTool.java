package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ISourceRange;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Views the source of a set of Java types the way JDT's own "Open Declaration" does: verbatim
 * source when available, falling back to the Enhanced Class Decompiler (ECD++) for binary types
 * with no attached source, when that plugin is installed.
 */
public class ViewSourceTool implements McpTool {

    private static final String DECOMPILER_BUNDLE = "io.github.nbauma109.decompiler";

    @Override
    public String name() {
        return "view_source";
    }

    @Override
    public String description() {
        return "View the source of a set of Java types by fully-qualified name, given the project they're "
                + "resolved from. Returns verbatim source for types backed by a compilation unit; for binary "
                + "types with no attached source, decompiles via the Enhanced Class Decompiler (ECD++) plugin "
                + "if it is installed, otherwise reports an error for that type. Output is line-numbered like "
                + "`cat -n`.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the types are resolved from");
        Schemas.stringArrayProp(schema, "fqNames",
                "Fully-qualified type names to view, e.g. [\"com.example.Foo\", \"java.util.ArrayList\"]");
        return Schemas.required(schema, "projectName", "fqNames");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        Set<String> fqNames = new LinkedHashSet<>(Schemas.optStringSet(arguments, "fqNames"));
        if (fqNames.isEmpty()) {
            throw new IllegalArgumentException("fqNames must not be empty");
        }

        JsonArray results = new JsonArray();
        for (String fqName : fqNames) {
            results.add(viewOne(projectName, fqName));
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }

    private JsonObject viewOne(String projectName, String fqName) {
        JsonObject entry = new JsonObject();
        entry.addProperty("fqName", fqName);
        try {
            IType type = JdtLookup.findType(projectName, fqName);
            if (!type.isBinary()) {
                ICompilationUnit unit = JdtLookup.compilationUnit(type);
                String unitSource = unit.getSource();
                ISourceRange range = type.getSourceRange();
                entry.addProperty("origin", "source");
                entry.addProperty("source", SourceFormatting.numbered(unitSource, range));
                return entry;
            }

            IClassFile classFile = type.getClassFile();
            String source = classFile.getSource();
            if (source != null) {
                entry.addProperty("origin", "attachedSource");
                entry.addProperty("source", SourceFormatting.numbered(source, 1));
                return entry;
            }

            return decompile(classFile, entry);
        } catch (RuntimeException | org.eclipse.core.runtime.CoreException e) {
            entry.addProperty("origin", "error");
            entry.addProperty("error", e.getMessage());
            return entry;
        }
    }

    private JsonObject decompile(IClassFile classFile, JsonObject entry) {
        if (Platform.getBundle(DECOMPILER_BUNDLE) == null) {
            entry.addProperty("origin", "error");
            entry.addProperty("error", "Type is binary with no attached source, and the Enhanced Class Decompiler "
                    + "(" + DECOMPILER_BUNDLE + ") is not installed");
            return entry;
        }

        try {
            char[] decompiled = Decompiler.decompile(classFile);
            if (decompiled == null) {
                entry.addProperty("origin", "error");
                entry.addProperty("error", "Decompiler produced no output for this type");
                return entry;
            }
            entry.addProperty("origin", "decompiled:" + Decompiler.decompilerType());
            entry.addProperty("source", SourceFormatting.numbered(new String(decompiled), 1));
            return entry;
        } catch (Exception e) {
            entry.addProperty("origin", "error");
            entry.addProperty("error", "Decompilation failed: " + e.getMessage());
            return entry;
        }
    }
}
