package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists every project in the workspace with its open/closed state and natures. */
public class ListProjectsTool implements McpTool {

    @Override
    public String name() {
        return "list_projects";
    }

    @Override
    public String description() {
        return "List all projects in the Eclipse workspace with their open/closed state and detected natures "
                + "(Java, etc).";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        JsonArray projects = new JsonArray();
        for (IProject project : Workspace.root().getProjects()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", project.getName());
            entry.addProperty("open", project.isOpen());
            boolean isJava = false;
            if (project.isOpen()) {
                IJavaProject javaProject = JavaCore.create(project);
                isJava = javaProject != null && javaProject.exists();
            }
            entry.addProperty("javaProject", isJava);
            projects.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(projects);
    }
}
