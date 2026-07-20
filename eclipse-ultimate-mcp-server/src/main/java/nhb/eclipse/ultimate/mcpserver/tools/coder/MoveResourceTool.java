package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Moves a file or folder to a different path, optionally into a different project. */
public class MoveResourceTool implements McpTool {

    @Override
    public String name() {
        return "move_resource";
    }

    @Override
    public String description() {
        return "Move a file or folder to a new path (optionally in a different project). Plain resource move, "
                + "not Java-aware — use refactor_move_java_type to move a .java file and update references.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project currently containing the resource");
        Schemas.prop(schema, "resourcePath", "string", "Current path, relative to the project root");
        Schemas.prop(schema, "targetProjectName", "string", "Destination project (defaults to the same project)");
        Schemas.prop(schema, "targetPath", "string", "Destination path, relative to the destination project root");
        return Schemas.required(schema, "projectName", "resourcePath", "targetPath");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String resourcePath = Schemas.requireString(arguments, "resourcePath");
        String targetProjectName = Schemas.optString(arguments, "targetProjectName", projectName);
        String targetPath = Schemas.requireString(arguments, "targetPath");

        IProject project = CoderResources.project(projectName);
        IResource resource = CoderResources.resource(project, resourcePath);
        IProject targetProject = CoderResources.project(targetProjectName);
        org.eclipse.core.runtime.IPath destination = targetProject.getFullPath().append(new Path(targetPath));
        resource.move(destination, true, new NullProgressMonitor());
        return "Moved " + resourcePath + " to " + targetProjectName + "/" + targetPath;
    }
}
