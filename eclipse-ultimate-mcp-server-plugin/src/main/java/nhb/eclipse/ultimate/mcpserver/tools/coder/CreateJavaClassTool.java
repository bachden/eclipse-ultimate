package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Creates a complete Java class from structured class and member definitions.
 */
public class CreateJavaClassTool implements McpTool {

    @Override
    public String name() {
        return "create_java_class";
    }

    @Override
    public String description() {
        return "Create and format a complete Java class in a source folder from structured metadata. One call can "
                + "define imports, annotations, inheritance, fields, constructors, and implemented method bodies. "
                + "The generated source is syntax-checked before it is written and opens in the Eclipse editor.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project in which to create the class");
        Schemas.prop(schema, "sourceFolder", "string",
                "Optional project-relative source folder; defaults to src/main/java or the first non-test source root");
        Schemas.prop(schema, "packageName", "string", "Java package name; use an empty string for the default package");
        Schemas.prop(schema, "className", "string", "Simple class name without .java");
        addStringArray(schema, "modifiers", "Class modifiers; defaults to [public]");
        Schemas.prop(schema, "typeParameters", "string",
                "Optional complete type parameter clause, e.g. <T extends Foo>");
        Schemas.prop(schema, "extendsType", "string", "Optional superclass source name");
        addStringArray(schema, "implementsTypes", "Implemented interface source names");
        addStringArray(schema, "imports", "Explicit imports, with optional leading static");
        Schemas.prop(schema, "javadoc", "string", "Optional complete class Javadoc comment");
        addStringArray(schema, "annotations", "Class annotations as Java source");
        addFields(schema);
        addConstructors(schema);
        addMethods(schema);
        Schemas.prop(schema, "openInEditor", "boolean", "Open the generated class in Eclipse (default true)");
        return Schemas.required(schema, "projectName", "packageName", "className");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String packageName = Schemas.requireString(arguments, "packageName");
        String className = Schemas.requireString(arguments, "className");

        IJavaProject javaProject = CoderJdt.javaProject(projectName);
        validateNames(javaProject, packageName, className);
        IPackageFragmentRoot root = selectSourceRoot(javaProject,
                Schemas.optString(arguments, "sourceFolder", "").trim());

        String source = buildSource(arguments);
        validateSyntax(javaProject, className + ".java", source);
        String formatted = format(javaProject, source);

        IPackageFragment packageFragment = packageName.isEmpty() ? root.getPackageFragment("")
                : root.createPackageFragment(packageName, true, new NullProgressMonitor());
        ICompilationUnit existing = packageFragment.getCompilationUnit(className + ".java");
        if (existing.exists()) {
            throw new IllegalArgumentException(
                    "Java class already exists: " + packageName + (packageName.isEmpty() ? "" : ".") + className);
        }

        ICompilationUnit unit = packageFragment.createCompilationUnit(className + ".java", formatted, false,
                new NullProgressMonitor());
        IResource resource = unit.getResource();
        if (!(resource instanceof IFile)) {
            throw new IllegalStateException(
                    "Generated compilation unit has no workspace file: " + unit.getHandleIdentifier());
        }
        IFile file = (IFile) resource;

        if (Schemas.optBoolean(arguments, "openInEditor", true)) {
            open(file);
        }

        return "Created Java class " + (packageName.isEmpty() ? className : packageName + "." + className) + " at "
                + file.getProjectRelativePath();
    }

    static String buildSource(JsonObject arguments) {
        String packageName = Schemas.requireString(arguments, "packageName");
        String className = Schemas.requireString(arguments, "className");
        StringBuilder source = new StringBuilder();

        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }

        Set<String> imports = new LinkedHashSet<>(stringArray(arguments, "imports"));
        imports.stream().sorted().forEach(value -> {
            String normalized = value.startsWith("import ") ? value.substring("import ".length()).trim() : value.trim();
            if (normalized.endsWith(";")) {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            }
            source.append("import ").append(normalized).append(";\n");
        });
        if (!imports.isEmpty()) {
            source.append('\n');
        }

        appendDocAndAnnotations(source, arguments, "");
        List<String> modifiers = stringArray(arguments, "modifiers");
        if (modifiers.isEmpty()) {
            modifiers = List.of("public");
        }
        source.append(joinTokens(modifiers)).append(" class ").append(className);
        appendOptional(source, Schemas.optString(arguments, "typeParameters", ""));
        String extendsType = Schemas.optString(arguments, "extendsType", "").trim();
        if (!extendsType.isEmpty()) {
            source.append(" extends ").append(extendsType);
        }
        List<String> interfaces = stringArray(arguments, "implementsTypes");
        if (!interfaces.isEmpty()) {
            source.append(" implements ").append(String.join(", ", interfaces));
        }
        source.append(" {\n");

        appendFields(source, arguments);
        appendConstructors(source, arguments, className);
        appendMethods(source, arguments);

        source.append("}\n");
        return source.toString();
    }

    private static void appendFields(StringBuilder source, JsonObject arguments) {
        JsonArray fields = objectArray(arguments, "fields");
        for (JsonElement element : fields) {
            JsonObject field = element.getAsJsonObject();
            source.append('\n');
            appendDocAndAnnotations(source, field, "    ");
            List<String> modifiers = stringArray(field, "modifiers");
            if (modifiers.isEmpty()) {
                modifiers = List.of("private");
            }
            source.append("    ").append(joinTokens(modifiers)).append(' ').append(Schemas.requireString(field, "type"))
                    .append(' ').append(Schemas.requireString(field, "name"));
            String initializer = Schemas.optString(field, "initializer", "").trim();
            if (!initializer.isEmpty()) {
                source.append(" = ").append(initializer);
            }
            source.append(";\n");
        }
    }

    private static void appendConstructors(StringBuilder source, JsonObject arguments, String className) {
        JsonArray constructors = objectArray(arguments, "constructors");
        for (JsonElement element : constructors) {
            JsonObject constructor = element.getAsJsonObject();
            source.append('\n');
            appendDocAndAnnotations(source, constructor, "    ");
            List<String> modifiers = stringArray(constructor, "modifiers");
            if (modifiers.isEmpty()) {
                modifiers = List.of("public");
            }
            source.append("    ").append(joinTokens(modifiers)).append(' ').append(className).append('(');
            appendParameters(source, constructor);
            source.append(')');
            appendThrows(source, constructor);
            appendBody(source, constructor, "constructor " + className);
        }
    }

    private static void appendMethods(StringBuilder source, JsonObject arguments) {
        JsonArray methods = objectArray(arguments, "methods");
        for (JsonElement element : methods) {
            JsonObject method = element.getAsJsonObject();
            source.append('\n');
            appendDocAndAnnotations(source, method, "    ");
            List<String> modifiers = stringArray(method, "modifiers");
            if (modifiers.isEmpty()) {
                modifiers = List.of("public");
            }
            source.append("    ").append(joinTokens(modifiers)).append(' ');
            appendOptional(source, Schemas.optString(method, "typeParameters", ""));
            source.append(Schemas.requireString(method, "returnType")).append(' ')
                    .append(Schemas.requireString(method, "name")).append('(');
            appendParameters(source, method);
            source.append(')');
            appendThrows(source, method);

            boolean declarationOnly = modifiers.contains("abstract") || modifiers.contains("native");
            if (declarationOnly) {
                if (method.has("body") && !method.get("body").isJsonNull()
                        && !method.get("body").getAsString().isBlank()) {
                    throw new IllegalArgumentException(
                            "Abstract/native method cannot have a body: " + method.get("name").getAsString());
                }
                source.append(";\n");
            } else {
                appendBody(source, method, "method " + method.get("name").getAsString());
            }
        }
    }

    private static void appendParameters(StringBuilder source, JsonObject owner) {
        JsonArray parameters = objectArray(owner, "parameters");
        List<String> declarations = new ArrayList<>();
        for (JsonElement element : parameters) {
            JsonObject parameter = element.getAsJsonObject();
            List<String> tokens = new ArrayList<>();
            tokens.addAll(stringArray(parameter, "annotations"));
            tokens.addAll(stringArray(parameter, "modifiers"));
            tokens.add(Schemas.requireString(parameter, "type"));
            tokens.add(Schemas.requireString(parameter, "name"));
            declarations.add(joinTokens(tokens));
        }
        source.append(String.join(", ", declarations));
    }

    private static void appendThrows(StringBuilder source, JsonObject owner) {
        List<String> exceptions = stringArray(owner, "throws");
        if (!exceptions.isEmpty()) {
            source.append(" throws ").append(String.join(", ", exceptions));
        }
    }

    private static void appendBody(StringBuilder source, JsonObject owner, String label) {
        if (!owner.has("body") || owner.get("body").isJsonNull()) {
            throw new IllegalArgumentException("Missing body for " + label);
        }
        String body = owner.get("body").getAsString();
        source.append(" {\n");
        if (!body.isBlank()) {
            for (String line : body.split("\\R", -1)) {
                source.append("        ").append(line).append('\n');
            }
        }
        source.append("    }\n");
    }

    private static void appendDocAndAnnotations(StringBuilder source, JsonObject object, String indent) {
        String javadoc = Schemas.optString(object, "javadoc", "").trim();
        if (!javadoc.isEmpty()) {
            for (String line : javadoc.split("\\R")) {
                source.append(indent).append(line).append('\n');
            }
        }
        for (String annotation : stringArray(object, "annotations")) {
            source.append(indent).append(annotation).append('\n');
        }
    }

    private static IPackageFragmentRoot selectSourceRoot(IJavaProject javaProject, String requested)
            throws JavaModelException {
        List<IPackageFragmentRoot> roots = new ArrayList<>();
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            if (root.getKind() == IPackageFragmentRoot.K_SOURCE && root.getResource() != null) {
                roots.add(root);
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Java project has no workspace source folders: " + javaProject.getElementName());
        }

        if (!requested.isEmpty()) {
            String normalized = requested.startsWith("/") ? requested.substring(1) : requested;
            for (IPackageFragmentRoot root : roots) {
                String path = root.getResource().getProjectRelativePath().toString();
                if (path.equals(normalized)) {
                    return root;
                }
            }
            throw new IllegalArgumentException(
                    "Source folder not found in " + javaProject.getElementName() + ": " + requested);
        }

        roots.sort(Comparator.comparingInt(CreateJavaClassTool::sourceRootScore));
        return roots.get(0);
    }

    private static int sourceRootScore(IPackageFragmentRoot root) {
        String path = root.getResource().getProjectRelativePath().toString();
        if ("src/main/java".equals(path)) {
            return 0;
        }
        if (path.contains("/main/")) {
            return 10;
        }
        if (path.contains("/test/") || path.startsWith("test")) {
            return 100;
        }
        return 50;
    }

    private static void validateNames(IJavaProject javaProject, String packageName, String className) {
        String source = javaProject.getOption(JavaCore.COMPILER_SOURCE, true);
        String compliance = javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true);
        String preview = javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true);

        IStatus typeStatus = JavaConventions.validateJavaTypeName(className, source, compliance, preview);
        if (typeStatus.matches(IStatus.ERROR)) {
            throw new IllegalArgumentException("Invalid Java class name: " + typeStatus.getMessage());
        }
        if (!packageName.isEmpty()) {
            IStatus packageStatus = JavaConventions.validatePackageName(packageName, source, compliance);
            if (packageStatus.matches(IStatus.ERROR)) {
                throw new IllegalArgumentException("Invalid Java package name: " + packageStatus.getMessage());
            }
        }
    }

    private static void validateSyntax(IJavaProject javaProject, String unitName, String source) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setProject(javaProject);
        parser.setCompilerOptions(javaProject.getOptions(true));
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setUnitName(unitName);
        parser.setResolveBindings(false);
        parser.setSource(source.toCharArray());

        CompilationUnit unit = (CompilationUnit) parser.createAST(new NullProgressMonitor());
        List<String> errors = new ArrayList<>();
        for (IProblem problem : unit.getProblems()) {
            if (problem.isError()) {
                errors.add("line " + problem.getSourceLineNumber() + ": " + problem.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Generated Java source has syntax errors: " + String.join("; ", errors));
        }
    }

    private static String format(IJavaProject javaProject, String source) throws Exception {
        Map<String, String> options = javaProject.getOptions(true);
        CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
        TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, source, 0,
                source.length(), 0, "\n");
        if (edit == null) {
            throw new IllegalArgumentException("JDT formatter could not parse the generated Java class");
        }

        IDocument document = new Document(source);
        edit.apply(document);
        return document.get();
    }

    private static void open(IFile file) throws Exception {
        Exception[] failure = new Exception[1];
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                IWorkbenchPage page = window == null ? null : window.getActivePage();
                if (page != null) {
                    IDE.openEditor(page, file);
                }
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static void appendOptional(StringBuilder source, String value) {
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            source.append(' ').append(normalized);
        }
    }

    private static String joinTokens(List<String> tokens) {
        return String.join(" ", tokens.stream().map(String::trim).filter(value -> !value.isEmpty()).toList());
    }

    private static List<String> stringArray(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return List.of();
        }
        if (!object.get(name).isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }

        List<String> values = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(name)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " must contain only strings");
            }
            values.add(element.getAsString());
        }
        return values;
    }

    private static JsonArray objectArray(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return new JsonArray();
        }
        if (!object.get(name).isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        JsonArray array = object.getAsJsonArray(name);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(name + " must contain only objects");
            }
        }
        return array;
    }

    private static void addFields(JsonObject schema) {
        JsonObject field = objectSchema();
        addProperty(field, "javadoc", scalar("string", "Optional complete field Javadoc comment"));
        addStringArray(field, "annotations", "Field annotations");
        addStringArray(field, "modifiers", "Field modifiers; defaults to [private]");
        addProperty(field, "type", scalar("string", "Field Java type"));
        addProperty(field, "name", scalar("string", "Field name"));
        addProperty(field, "initializer", scalar("string", "Optional Java initializer expression"));
        require(field, "type", "name");
        addObjectArray(schema, "fields", "Fields to create", field);
    }

    private static void addConstructors(JsonObject schema) {
        JsonObject constructor = objectSchema();
        addProperty(constructor, "javadoc", scalar("string", "Optional complete constructor Javadoc comment"));
        addStringArray(constructor, "annotations", "Constructor annotations");
        addStringArray(constructor, "modifiers", "Constructor modifiers; defaults to [public]");
        addParameters(constructor);
        addStringArray(constructor, "throws", "Thrown exception type names");
        addProperty(constructor, "body", scalar("string", "Constructor body statements without outer braces"));
        require(constructor, "body");
        addObjectArray(schema, "constructors", "Constructors to create", constructor);
    }

    private static void addMethods(JsonObject schema) {
        JsonObject method = objectSchema();
        addProperty(method, "javadoc", scalar("string", "Optional complete method Javadoc comment"));
        addStringArray(method, "annotations", "Method annotations");
        addStringArray(method, "modifiers", "Method modifiers; defaults to [public]");
        addProperty(method, "typeParameters", scalar("string", "Optional complete method type parameter clause"));
        addProperty(method, "returnType", scalar("string", "Method return type"));
        addProperty(method, "name", scalar("string", "Method name"));
        addParameters(method);
        addStringArray(method, "throws", "Thrown exception type names");
        addProperty(method, "body",
                scalar("string", "Method body statements without outer braces; omit only for abstract/native methods"));
        require(method, "returnType", "name");
        addObjectArray(schema, "methods", "Methods to create, including their implementations", method);
    }

    private static void addParameters(JsonObject ownerSchema) {
        JsonObject parameter = objectSchema();
        addStringArray(parameter, "annotations", "Parameter annotations");
        addStringArray(parameter, "modifiers", "Parameter modifiers, e.g. final");
        addProperty(parameter, "type", scalar("string", "Parameter Java type, including ... for varargs"));
        addProperty(parameter, "name", scalar("string", "Parameter name"));
        require(parameter, "type", "name");
        addObjectArray(ownerSchema, "parameters", "Parameters in declaration order", parameter);
    }

    private static void addObjectArray(JsonObject schema, String name, String description, JsonObject itemSchema) {
        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        array.addProperty("description", description);
        array.add("items", itemSchema);
        addProperty(schema, name, array);
    }

    private static void addStringArray(JsonObject schema, String name, String description) {
        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        array.addProperty("description", description);
        array.add("items", scalar("string", "Java source value"));
        addProperty(schema, name, array);
    }

    private static JsonObject objectSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    private static JsonObject scalar(String type, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        schema.addProperty("description", description);
        return schema;
    }

    private static void addProperty(JsonObject schema, String name, JsonObject property) {
        schema.getAsJsonObject("properties").add(name, property);
    }

    private static void require(JsonObject schema, String... names) {
        JsonArray required = new JsonArray();
        for (String name : names) {
            required.add(name);
        }
        schema.add("required", required);
    }
}
