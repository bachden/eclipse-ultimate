package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns the verbatim source of a single method, matched by (simple) name and parameter count. */
public class GetMethodSourceTool implements McpTool {

    @Override
    public String name() {
        return "get_method_source";
    }

    @Override
    public String description() {
        return "Get the verbatim source of a single method on a type, given the type's fully-qualified name and "
                + "the method name. If the method is overloaded, optionally disambiguate with parameterCount.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the type belongs to");
        Schemas.prop(schema, "fqName", "string", "Fully-qualified type name, e.g. com.example.Foo");
        Schemas.prop(schema, "methodName", "string", "Method name to look up");
        Schemas.prop(schema, "parameterCount", "integer", "Optional parameter count to disambiguate overloads");
        return Schemas.required(schema, "projectName", "fqName", "methodName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqName = Schemas.requireString(arguments, "fqName");
        String methodName = Schemas.requireString(arguments, "methodName");
        int parameterCount = Schemas.optInt(arguments, "parameterCount", -1);

        IType type = JdtLookup.findType(projectName, fqName);
        IMethod match = null;
        for (IMethod method : type.getMethods()) {
            if (!method.getElementName().equals(methodName)) {
                continue;
            }
            if (parameterCount >= 0 && method.getParameterTypes().length != parameterCount) {
                continue;
            }
            if (match != null) {
                throw new IllegalArgumentException("Method " + methodName + " is overloaded on " + fqName
                        + "; disambiguate with parameterCount");
            }
            match = method;
        }
        if (match == null) {
            throw new IllegalArgumentException("Method not found: " + methodName + " on " + fqName);
        }

        String unitSource = JdtLookup.compilationUnit(type).getSource();
        ISourceRange range = match.getSourceRange();
        return SourceFormatting.numbered(unitSource, range);
    }
}
