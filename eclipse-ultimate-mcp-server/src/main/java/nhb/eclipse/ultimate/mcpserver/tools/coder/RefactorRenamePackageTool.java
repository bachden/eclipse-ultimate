package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Renames a package and updates all references across the workspace. */
public class RefactorRenamePackageTool implements McpTool {

    @Override
    public String name() {
        return "refactor_rename_package";
    }

    @Override
    public String description() {
        return "Rename a package (and its folder) and update every reference across the workspace, using the "
                + "same refactoring engine as Eclipse's Rename wizard.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the package belongs to");
        Schemas.prop(schema, "packageName", "string", "Current package name, e.g. com.example.old");
        Schemas.prop(schema, "newName", "string", "New package name, e.g. com.example.newname");
        return Schemas.required(schema, "projectName", "packageName", "newName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String packageName = Schemas.requireString(arguments, "packageName");
        String newName = Schemas.requireString(arguments, "newName");

        IPackageFragment fragment = CoderJdt.findPackage(projectName, packageName);

        RenameJavaElementDescriptor descriptor = (RenameJavaElementDescriptor) RefactoringCore
                .getRefactoringContribution(IJavaRefactorings.RENAME_PACKAGE).createDescriptor();
        descriptor.setProject(projectName);
        descriptor.setJavaElement(fragment);
        descriptor.setNewName(newName);
        descriptor.setUpdateReferences(true);

        RefactorSupport.run(descriptor);
        return "Renamed package " + packageName + " to " + newName;
    }
}
