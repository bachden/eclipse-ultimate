package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Renames a Java type and updates all references across the workspace, via LTK's rename-type refactoring. */
public class RefactorRenameJavaTypeTool implements McpTool {

    @Override
    public String name() {
        return "refactor_rename_java_type";
    }

    @Override
    public String description() {
        return "Rename a Java type (class/interface/enum/record) and update every reference across the "
                + "workspace, including the file name. Uses the same refactoring engine as Eclipse's Rename wizard.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the type belongs to");
        Schemas.prop(schema, "fqName", "string", "Current fully-qualified type name, e.g. com.example.Foo");
        Schemas.prop(schema, "newName", "string", "New simple type name (no package)");
        return Schemas.required(schema, "projectName", "fqName", "newName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqName = Schemas.requireString(arguments, "fqName");
        String newName = Schemas.requireString(arguments, "newName");

        IType type = CoderJdt.findType(projectName, fqName);

        RenameJavaElementDescriptor descriptor = (RenameJavaElementDescriptor) RefactoringCore
                .getRefactoringContribution(IJavaRefactorings.RENAME_TYPE).createDescriptor();
        descriptor.setProject(projectName);
        descriptor.setJavaElement(type);
        descriptor.setNewName(newName);
        descriptor.setUpdateReferences(true);

        RefactorSupport.run(descriptor);
        return "Renamed " + fqName + " to " + newName;
    }
}
