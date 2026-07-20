package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.MoveDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Moves a Java type to a different package and updates all references across
 * the workspace.
 */
public class RefactorMoveJavaTypeTool implements McpTool {

    @Override
    public String name() {
        return "refactor_move_java_type";
    }

    @Override
    public String description() {
        return "Move a Java type (class/interface/enum/record) to a different package in the same project and "
                + "update every reference across the workspace. The destination package must already exist.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the type belongs to");
        Schemas.prop(schema, "fqName", "string", "Current fully-qualified type name, e.g. com.example.Foo");
        Schemas.prop(schema, "targetPackage", "string", "Destination package name, e.g. com.example.util");
        return Schemas.required(schema, "projectName", "fqName", "targetPackage");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqName = Schemas.requireString(arguments, "fqName");
        String targetPackage = Schemas.requireString(arguments, "targetPackage");

        IType type = CoderJdt.findType(projectName, fqName);
        validateMovableSourceType(type, projectName, fqName);
        IPackageFragment destination = CoderJdt.findPackage(projectName, targetPackage);
        if (destination.getResource() == null) {
            throw new IllegalArgumentException(
                    "Destination package is not backed by a workspace resource: " + targetPackage);
        }

        MoveDescriptor descriptor = (MoveDescriptor) RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE)
                .createDescriptor();
        descriptor.setProject(projectName);
        descriptor.setMoveResources(new IFile[0], new IFolder[0],
                new ICompilationUnit[] { type.getCompilationUnit() });
        descriptor.setDestination(destination);
        descriptor.setUpdateReferences(true);
        descriptor.setUpdateQualifiedNames(true);

        RefactorSupport.run(descriptor);
        return "Moved " + fqName + " to " + targetPackage;
    }

    private static void validateMovableSourceType(IType type, String projectName, String fqName) {
        IResource resource = type.getResource();
        ICompilationUnit unit = type.getCompilationUnit();
        if (resource == null || unit == null || !unit.exists()) {
            throw new IllegalArgumentException("Type is not a workspace source type and cannot be moved: " + fqName);
        }
        IType primaryType = unit.findPrimaryType();
        if (primaryType == null || !type.equals(primaryType)) {
            throw new IllegalArgumentException(
                    "Only a primary top-level source type can be moved to a package: " + fqName);
        }
        if (!projectName.equals(resource.getProject().getName())) {
            throw new IllegalArgumentException("Type " + fqName + " resolves to project "
                    + resource.getProject().getName() + ", not " + projectName);
        }
        if (!resource.exists()) {
            throw new IllegalArgumentException("Source resource does not exist for type: " + fqName);
        }
    }
}
