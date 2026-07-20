package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.corext.refactoring.ExceptionInfo;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Changes a method or constructor signature and updates declarations and call
 * sites through JDT's Change Signature refactoring.
 */
public class RefactorChangeMethodSignatureTool implements McpTool {

    @Override
    public String name() {
        return "refactor_change_method_signature";
    }

    @Override
    public String description() {
        return "Change a Java method or constructor signature and update declarations and call sites across the "
                + "workspace. A single call can rename the method, change return type/visibility, add, remove, "
                + "rename, retype or reorder parameters, and replace the throws list.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project containing the declaring type");
        Schemas.prop(schema, "declaringType", "string", "Fully-qualified declaring type, e.g. com.example.Service");
        Schemas.prop(schema, "methodName", "string",
                "Current method name. Use the simple type name or <init> for a constructor");
        addStringArray(schema, "currentParameterTypes",
                "Current parameter types used to identify the overload, in declaration order");

        Schemas.prop(schema, "newName", "string", "Optional new method name");
        Schemas.prop(schema, "newReturnType", "string", "Optional new return type; not valid for constructors");
        addVisibility(schema);
        addParameters(schema);
        addStringArray(schema, "thrownExceptions",
                "Optional final throws list using fully-qualified exception type names");
        Schemas.prop(schema, "keepDelegate", "boolean",
                "Keep a delegating method with the old signature when JDT supports it (default false)");
        Schemas.prop(schema, "deprecateDelegate", "boolean",
                "Mark the generated delegate deprecated (default false; requires keepDelegate)");

        return Schemas.required(schema, "projectName", "declaringType", "methodName", "currentParameterTypes");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String declaringType = Schemas.requireString(arguments, "declaringType");
        String methodName = Schemas.requireString(arguments, "methodName");
        List<String> currentParameterTypes = stringArray(arguments, "currentParameterTypes", true);

        IType type = CoderJdt.findType(projectName, declaringType);
        if (type.getCompilationUnit() == null) {
            throw new IllegalArgumentException("Method must be declared in workspace source: " + declaringType);
        }

        IMethod method = findMethod(type, methodName, currentParameterTypes);
        ChangeSignatureProcessor processor = new ChangeSignatureProcessor(method);
        RefactoringStatus initialStatus = processor.checkInitialConditions(new NullProgressMonitor());
        if (initialStatus.hasError()) {
            throw new IllegalArgumentException(
                    "Cannot change method signature: " + statusMessage(initialStatus, RefactoringStatus.ERROR));
        }

        configureNameAndReturnType(arguments, processor, method);
        configureVisibility(arguments, processor);
        configureParameters(arguments, processor);
        configureExceptions(arguments, processor, CoderJdt.javaProject(projectName));
        configureDelegate(arguments, processor);

        RefactoringStatus signatureStatus = processor.checkSignature();
        if (signatureStatus.hasError()) {
            throw new IllegalArgumentException(
                    "Invalid target signature: " + statusMessage(signatureStatus, RefactoringStatus.ERROR));
        }

        String oldSignature = processor.getOldMethodSignature();
        String newSignature = processor.getNewMethodSignature();
        if (processor.isSignatureSameAsInitial()) {
            throw new IllegalArgumentException("Requested method signature is unchanged: " + oldSignature);
        }

        RefactorSupport.run(new ProcessorBasedRefactoring(processor));
        return "Changed method signature: " + oldSignature + " -> " + newSignature;
    }

    private static void configureNameAndReturnType(JsonObject arguments, ChangeSignatureProcessor processor,
            IMethod method) throws JavaModelException {
        String newName = Schemas.optString(arguments, "newName", processor.getMethodName());
        String newReturnType = Schemas.optString(arguments, "newReturnType", processor.getReturnTypeString());
        boolean nameChanged = !newName.equals(processor.getMethodName());
        boolean returnTypeChanged = !newReturnType.equals(processor.getReturnTypeString());

        if ((nameChanged || returnTypeChanged) && !processor.canChangeNameAndReturnType()) {
            throw new IllegalArgumentException(method.isConstructor() ? "Constructors cannot change name or return type"
                    : "JDT cannot change the name or return type of this method");
        }
        if (nameChanged) {
            processor.setNewMethodName(newName);
        }
        if (returnTypeChanged) {
            processor.setNewReturnTypeName(newReturnType);
        }
    }

    private static void configureVisibility(JsonObject arguments, ChangeSignatureProcessor processor) {
        if (!arguments.has("visibility") || arguments.get("visibility").isJsonNull()) {
            return;
        }

        String visibility = arguments.get("visibility").getAsString();
        int flags = switch (visibility) {
        case "public" -> Flags.AccPublic;
        case "protected" -> Flags.AccProtected;
        case "package" -> 0;
        case "private" -> Flags.AccPrivate;
        default -> throw new IllegalArgumentException(
                "visibility must be public, protected, package or private: " + visibility);
        };

        boolean available = false;
        try {
            for (int candidate : processor.getAvailableVisibilities()) {
                if (candidate == flags) {
                    available = true;
                    break;
                }
            }
        } catch (JavaModelException e) {
            throw new IllegalArgumentException("Could not inspect available method visibilities", e);
        }
        if (!available) {
            throw new IllegalArgumentException("Visibility is not valid for this method: " + visibility);
        }
        processor.setVisibility(flags);
    }

    private static void configureParameters(JsonObject arguments, ChangeSignatureProcessor processor) {
        if (!arguments.has("parameters") || arguments.get("parameters").isJsonNull()) {
            return;
        }
        if (!arguments.get("parameters").isJsonArray()) {
            throw new IllegalArgumentException("parameters must be an array");
        }

        List<ParameterInfo> original = new ArrayList<>(processor.getParameterInfos());
        List<ParameterInfo> configured = new ArrayList<>();
        Set<Integer> retainedIndexes = new HashSet<>();

        for (JsonElement element : arguments.getAsJsonArray("parameters")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Each parameter must be an object");
            }
            JsonObject spec = element.getAsJsonObject();
            String type = Schemas.requireString(spec, "type");
            String name = Schemas.requireString(spec, "name");

            if (spec.has("sourceIndex") && !spec.get("sourceIndex").isJsonNull()) {
                int sourceIndex = spec.get("sourceIndex").getAsInt();
                if (sourceIndex < 0 || sourceIndex >= original.size()) {
                    throw new IllegalArgumentException("Parameter sourceIndex out of range: " + sourceIndex);
                }
                if (!retainedIndexes.add(sourceIndex)) {
                    throw new IllegalArgumentException("Parameter sourceIndex is used more than once: " + sourceIndex);
                }

                ParameterInfo info = original.get(sourceIndex);
                info.setNewTypeName(type);
                info.setNewName(name);
                configured.add(info);
            } else {
                if (!spec.has("defaultValue") || spec.get("defaultValue").isJsonNull()) {
                    throw new IllegalArgumentException(
                            "A new parameter requires defaultValue for existing call sites: " + name);
                }
                String defaultValue = spec.get("defaultValue").getAsString();
                configured.add(ParameterInfo.createInfoForAddedParameter(type, name, defaultValue));
            }
        }

        for (int i = 0; i < original.size(); i++) {
            if (!retainedIndexes.contains(i)) {
                ParameterInfo deleted = original.get(i);
                deleted.markAsDeleted();
                configured.add(deleted);
            }
        }

        List<ParameterInfo> target = processor.getParameterInfos();
        target.clear();
        target.addAll(configured);
    }

    private static void configureExceptions(JsonObject arguments, ChangeSignatureProcessor processor,
            IJavaProject javaProject) throws JavaModelException {
        if (!arguments.has("thrownExceptions") || arguments.get("thrownExceptions").isJsonNull()) {
            return;
        }

        List<String> requested = stringArray(arguments, "thrownExceptions", false);
        Set<String> uniqueRequested = new LinkedHashSet<>(requested);
        if (uniqueRequested.size() != requested.size()) {
            throw new IllegalArgumentException("thrownExceptions contains duplicate type names");
        }

        List<ExceptionInfo> infos = processor.getExceptionInfos();
        Set<String> existing = new HashSet<>();
        for (ExceptionInfo info : infos) {
            String fqName = info.getFullyQualifiedName();
            existing.add(fqName);
            if (!uniqueRequested.contains(fqName)) {
                info.markAsDeleted();
            }
        }

        for (String fqName : uniqueRequested) {
            if (existing.contains(fqName)) {
                continue;
            }
            IType exceptionType = javaProject.findType(fqName);
            if (exceptionType == null || !exceptionType.exists()) {
                throw new IllegalArgumentException("Exception type not found on project classpath: " + fqName);
            }
            infos.add(ExceptionInfo.createInfoForAddedException(exceptionType));
        }
    }

    private static void configureDelegate(JsonObject arguments, ChangeSignatureProcessor processor) {
        boolean keepDelegate = Schemas.optBoolean(arguments, "keepDelegate", false);
        boolean deprecateDelegate = Schemas.optBoolean(arguments, "deprecateDelegate", false);
        if (deprecateDelegate && !keepDelegate) {
            throw new IllegalArgumentException("deprecateDelegate requires keepDelegate=true");
        }
        if (keepDelegate && !processor.canEnableDelegateUpdating()) {
            throw new IllegalArgumentException("JDT cannot keep a delegate for this method signature change");
        }
        processor.setDelegateUpdating(keepDelegate);
        processor.setDeprecateDelegates(deprecateDelegate);
    }

    private static IMethod findMethod(IType type, String requestedName, List<String> parameterTypes)
            throws JavaModelException {
        boolean constructor = "<init>".equals(requestedName) || type.getElementName().equals(requestedName);
        String elementName = constructor ? type.getElementName() : requestedName;
        String[] signatures = parameterTypes.stream().map(RefactorChangeMethodSignatureTool::jdtTypeSignature)
                .toArray(String[]::new);

        IMethod[] resolved = type.findMethods(type.getMethod(elementName, signatures));
        if (resolved != null) {
            List<IMethod> matching = new ArrayList<>();
            for (IMethod method : resolved) {
                if (method.isConstructor() == constructor) {
                    matching.add(method);
                }
            }
            if (matching.size() == 1) {
                return matching.get(0);
            }
        }

        List<IMethod> fallback = new ArrayList<>();
        for (IMethod method : type.getMethods()) {
            if (method.isConstructor() != constructor || !method.getElementName().equals(elementName)
                    || method.getNumberOfParameters() != parameterTypes.size()) {
                continue;
            }
            if (parametersMatch(method, parameterTypes)) {
                fallback.add(method);
            }
        }
        if (fallback.size() == 1) {
            return fallback.get(0);
        }
        if (fallback.isEmpty()) {
            throw new IllegalArgumentException("Method not found: " + type.getFullyQualifiedName() + "#" + requestedName
                    + "(" + String.join(", ", parameterTypes) + ")");
        }
        throw new IllegalArgumentException("Method selector is ambiguous; use more specific currentParameterTypes: "
                + type.getFullyQualifiedName() + "#" + requestedName);
    }

    private static boolean parametersMatch(IMethod method, List<String> requestedTypes) throws JavaModelException {
        String[] actualTypes = method.getParameterTypes();
        for (int i = 0; i < actualTypes.length; i++) {
            String actual = Signature.toString(Signature.getTypeErasure(actualTypes[i]));
            if (!simpleErasure(actual).equals(simpleErasure(requestedTypes.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String jdtTypeSignature(String type) {
        return Signature.createTypeSignature(type.replace("...", "[]"), true);
    }

    private static String simpleErasure(String type) {
        String value = type.replace("...", "[]").replaceAll("\\s+", "");
        StringBuilder erased = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            } else if (depth == 0) {
                erased.append(ch);
            }
        }

        String suffix = "";
        String base = erased.toString();
        while (base.endsWith("[]")) {
            suffix += "[]";
            base = base.substring(0, base.length() - 2);
        }
        int qualifier = Math.max(base.lastIndexOf('.'), base.lastIndexOf('$'));
        return (qualifier >= 0 ? base.substring(qualifier + 1) : base) + suffix;
    }

    private static List<String> stringArray(JsonObject arguments, String name, boolean required) {
        if (!arguments.has(name) || arguments.get(name).isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("Missing required argument: " + name);
            }
            return List.of();
        }
        if (!arguments.get(name).isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }

        List<String> values = new ArrayList<>();
        for (JsonElement element : arguments.getAsJsonArray(name)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " must contain only strings");
            }
            values.add(element.getAsString());
        }
        return values;
    }

    private static String statusMessage(RefactoringStatus status, int severity) {
        String message = status.getMessageMatchingSeverity(severity);
        return message == null || message.isBlank() ? status.toString() : message;
    }

    private static void addVisibility(JsonObject schema) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", "Optional final visibility");
        JsonArray values = new JsonArray();
        values.add("public");
        values.add("protected");
        values.add("package");
        values.add("private");
        property.add("enum", values);
        schema.getAsJsonObject("properties").add("visibility", property);
    }

    private static void addParameters(JsonObject schema) {
        JsonObject parameter = new JsonObject();
        parameter.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        properties.add("sourceIndex",
                scalar("integer", "Zero-based index of an existing parameter; omit to add a new parameter"));
        properties.add("type", scalar("string", "Final Java type, including [] or ... when applicable"));
        properties.add("name", scalar("string", "Final parameter name"));
        properties.add("defaultValue",
                scalar("string", "Java expression inserted at existing call sites for a new parameter"));
        parameter.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("type");
        required.add("name");
        parameter.add("required", required);

        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        array.addProperty("description",
                "Optional final parameter list. Existing parameters use sourceIndex; omitted indexes are deleted");
        array.add("items", parameter);
        schema.getAsJsonObject("properties").add("parameters", array);
    }

    private static void addStringArray(JsonObject schema, String name, String description) {
        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        array.addProperty("description", description);
        array.add("items", scalar("string", "Java type name"));
        schema.getAsJsonObject("properties").add(name, array);
    }

    private static JsonObject scalar(String type, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        schema.addProperty("description", description);
        return schema;
    }
}
