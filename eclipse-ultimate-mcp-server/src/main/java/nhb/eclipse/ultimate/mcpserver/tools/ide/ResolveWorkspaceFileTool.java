package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.net.URI;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Resolves an Eclipse workspace file to its backing filesystem location. */
public class ResolveWorkspaceFileTool implements McpTool {

    @Override
    public String name() {
        return "resolve_workspace_file";
    }

    @Override
    public String description() {
        return "Resolve a project-relative Eclipse workspace file to its absolute backing path. Uses the resource "
                + "location URI so linked files and files under linked folders resolve to their actual target.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Eclipse project containing the workspace file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        return Schemas.required(schema, "projectName", "filePath");
    }

    @Override
    public String execute(JsonObject arguments) {
        String projectName = Schemas.requireString(arguments, "projectName").trim();
        String filePath = Schemas.requireString(arguments, "filePath").trim();

        IProject project = Workspace.project(projectName);
        IResource resource = Workspace.resource(project, filePath);
        if (!(resource instanceof IFile file)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }

        URI locationUri = file.getLocationURI();
        if (locationUri == null) {
            throw new IllegalStateException("Workspace file has no backing location URI: " + file.getFullPath());
        }

        JsonObject result = new JsonObject();
        result.addProperty("projectName", project.getName());
        result.addProperty("workspacePath", file.getFullPath().toString());
        result.addProperty("projectRelativePath", file.getProjectRelativePath().toString());
        result.addProperty("exists", file.exists());
        result.addProperty("accessible", file.isAccessible());
        boolean directlyLinked = file.isLinked();
        boolean linkedInPath = file.isLinked(IResource.CHECK_ANCESTORS);
        result.addProperty("linked", linkedInPath);
        result.addProperty("directlyLinked", directlyLinked);
        result.addProperty("linkedThroughAncestor", linkedInPath && !directlyLinked);
        result.addProperty("virtual", file.isVirtual());
        result.addProperty("locationUri", locationUri.toString());

        URI rawLocationUri = file.getRawLocationURI();
        if (rawLocationUri != null) {
            result.addProperty("rawLocationUri", rawLocationUri.toString());
        }

        boolean localFile = "file".equalsIgnoreCase(locationUri.getScheme());
        result.addProperty("localFile", localFile);
        if (!localFile) {
            throw new IllegalStateException("Workspace file is backed by a non-local URI and has no filesystem "
                    + "absolute path: " + locationUri);
        }

        result.addProperty("absolutePath", Path.of(locationUri).toAbsolutePath().normalize().toString());
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
