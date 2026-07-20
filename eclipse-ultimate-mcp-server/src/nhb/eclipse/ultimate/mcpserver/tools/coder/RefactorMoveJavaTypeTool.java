package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.MoveDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Moves a Java type to a different package and updates all references across the workspace. */
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
        IPackageFragment destination = CoderJdt.findPackage(projectName, targetPackage);

        MoveDescriptor descriptor = (MoveDescriptor) RefactoringCore
                .getRefactoringContribution(IJavaRefactorings.MOVE).createDescriptor();
        descriptor.setProject(projectName);
        descriptor.setMoveMembers(new IMember[] { type });
        descriptor.setDestination(destination);
        descriptor.setUpdateReferences(true);

        RefactorSupport.run(descriptor);
        return "Moved " + fqName + " to " + targetPackage;
    }
}
