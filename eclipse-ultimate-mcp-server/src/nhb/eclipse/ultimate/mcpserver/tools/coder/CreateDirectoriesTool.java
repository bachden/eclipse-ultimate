package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Creates a folder (and any missing parents) in a project. */
public class CreateDirectoriesTool implements McpTool {

    @Override
    public String name() {
        return "create_directories";
    }

    @Override
    public String description() {
        return "Create a folder in a project, along with any missing parent folders. No-op if it already exists.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to create the folder in");
        Schemas.prop(schema, "folderPath", "string", "Path of the folder, relative to the project root");
        return Schemas.required(schema, "projectName", "folderPath");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String folderPath = Schemas.requireString(arguments, "folderPath");

        IProject project = CoderResources.project(projectName);
        IPath path = new Path(folderPath);
        for (int i = path.segmentCount(); i >= 1; i--) {
            IFolder folder = project.getFolder(path.removeLastSegments(i - 1));
            if (!folder.exists()) {
                folder.create(true, true, new NullProgressMonitor());
            }
        }
        return "Created " + folderPath;
    }
}
