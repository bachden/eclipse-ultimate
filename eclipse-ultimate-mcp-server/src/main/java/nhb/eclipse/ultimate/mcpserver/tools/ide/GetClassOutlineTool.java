package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Flags;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Returns a type's outline (fields + method signatures) without full bodies —
 * cheap way to see a class's shape, and forces the JDT model to refresh
 * against the file on disk (useful when compilation markers look stale).
 */
public class GetClassOutlineTool implements McpTool {

    @Override
    public String name() {
        return "get_class_outline";
    }

    @Override
    public String description() {
        return "Get a Java type's outline: fields and method signatures (no bodies), given its fully-qualified "
                + "name. Cheaper than get_source when you just need the shape of a class.";
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

        JsonObject result = new JsonObject();
        result.addProperty("type", type.getFullyQualifiedName());
        result.addProperty("kind", type.isInterface() ? "interface" : type.isEnum() ? "enum"
                : type.isRecord() ? "record" : type.isAnnotation() ? "annotation" : "class");
        result.addProperty("superclass", type.getSuperclassName());

        JsonArray interfaces = new JsonArray();
        for (String iface : type.getSuperInterfaceNames()) {
            interfaces.add(iface);
        }
        result.add("interfaces", interfaces);

        JsonArray fields = new JsonArray();
        for (IField field : type.getFields()) {
            JsonObject f = new JsonObject();
            f.addProperty("name", field.getElementName());
            f.addProperty("type", org.eclipse.jdt.core.Signature.toString(field.getTypeSignature()));
            f.addProperty("modifiers", Flags.toString(field.getFlags()));
            fields.add(f);
        }
        result.add("fields", fields);

        JsonArray methods = new JsonArray();
        for (IMethod method : type.getMethods()) {
            JsonObject m = new JsonObject();
            m.addProperty("signature", methodSignature(method));
            m.addProperty("modifiers", Flags.toString(method.getFlags()));
            methods.add(m);
        }
        result.add("methods", methods);

        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private String methodSignature(IMethod method) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(method.isConstructor() ? method.getDeclaringType().getElementName() : method.getElementName());
        sb.append('(');
        String[] paramTypes = method.getParameterTypes();
        String[] paramNames = method.getParameterNames();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(org.eclipse.jdt.core.Signature.toString(paramTypes[i])).append(' ').append(paramNames[i]);
        }
        sb.append(')');
        if (!method.isConstructor()) {
            sb.append(" : ").append(org.eclipse.jdt.core.Signature.toString(method.getReturnType()));
        }
        return sb.toString();
    }
}
