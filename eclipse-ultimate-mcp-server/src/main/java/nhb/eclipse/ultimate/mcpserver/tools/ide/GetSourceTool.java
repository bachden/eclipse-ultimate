package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ISourceRange;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns the verbatim, line-numbered source of a Java type given its fully-qualified name. */
public class GetSourceTool implements McpTool {

    @Override
    public String name() {
        return "get_source";
    }

    @Override
    public String description() {
        return "Get the verbatim source of a Java type (class/interface/enum/record), given its fully-qualified "
                + "name and the project it lives in. Output is line-numbered like `cat -n`.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the type belongs to");
        Schemas.prop(schema, "fqName", "string", "Fully-qualified type name, e.g. com.example.Foo");
        return Schemas.required(schema, "projectName", "fqName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqName = Schemas.requireString(arguments, "fqName");

        IType type = JdtLookup.findType(projectName, fqName);
        String unitSource = JdtLookup.compilationUnit(type).getSource();
        ISourceRange range = type.getSourceRange();
        return SourceFormatting.numbered(unitSource, range);
    }
}
