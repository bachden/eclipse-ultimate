package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Renames a file or folder within the same parent directory (plain resource move, no Java refactor). */
public class RenameFileTool implements McpTool {

    @Override
    public String name() {
        return "rename_file";
    }

    @Override
    public String description() {
        return "Rename a file or folder in place (same parent directory). This is a plain resource rename, not a "
                + "Java-aware refactor — use refactor_rename_java_type to rename a .java file and update references.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the resource");
        Schemas.prop(schema, "resourcePath", "string", "Current path of the file or folder, relative to the project root");
        Schemas.prop(schema, "newName", "string", "New simple name (no path segments)");
        return Schemas.required(schema, "projectName", "resourcePath", "newName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String resourcePath = Schemas.requireString(arguments, "resourcePath");
        String newName = Schemas.requireString(arguments, "newName");

        IProject project = CoderResources.project(projectName);
        IResource resource = CoderResources.resource(project, resourcePath);
        org.eclipse.core.runtime.IPath newPath = resource.getFullPath().removeLastSegments(1).append(newName);
        resource.move(newPath, true, new NullProgressMonitor());
        return "Renamed " + resourcePath + " to " + newName;
    }
}
