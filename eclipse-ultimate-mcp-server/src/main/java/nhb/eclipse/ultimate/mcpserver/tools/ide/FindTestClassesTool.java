package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Finds candidate JUnit test classes in a project by naming convention (*Test / Test*). */
public class FindTestClassesTool implements McpTool {

    @Override
    public String name() {
        return "find_test_classes";
    }

    @Override
    public String description() {
        return "Find candidate JUnit test classes in a project, matched by naming convention (ends with 'Test' "
                + "or 'Tests', or starts with 'Test').";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to search in");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        IProject project = Workspace.project(projectName);

        JsonArray results = new JsonArray();
        List<IFile> files = FileWalker.allFiles(project);
        for (IFile file : files) {
            if (!"java".equalsIgnoreCase(file.getFileExtension())) {
                continue;
            }
            String simpleName = file.getName().substring(0, file.getName().length() - ".java".length());
            if (!(simpleName.endsWith("Test") || simpleName.endsWith("Tests") || simpleName.startsWith("Test"))) {
                continue;
            }
            Object element = JavaCore.create(file);
            if (element instanceof ICompilationUnit) {
                for (IType type : ((ICompilationUnit) element).getTypes()) {
                    results.add(type.getFullyQualifiedName());
                }
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
