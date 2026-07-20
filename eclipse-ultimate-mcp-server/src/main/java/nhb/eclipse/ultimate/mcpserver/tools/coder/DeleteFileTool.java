package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Deletes a file or folder from a project. */
public class DeleteFileTool implements McpTool {

    @Override
    public String name() {
        return "delete_file";
    }

    @Override
    public String description() {
        return "Delete a file or folder (recursively) from a project.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the resource");
        Schemas.prop(schema, "resourcePath", "string", "Path of the file or folder, relative to the project root");
        return Schemas.required(schema, "projectName", "resourcePath");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String resourcePath = Schemas.requireString(arguments, "resourcePath");

        IProject project = CoderResources.project(projectName);
        IResource resource = CoderResources.resource(project, resourcePath);
        resource.delete(true, new NullProgressMonitor());
        return "Deleted " + resourcePath;
    }
}
