package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Creates a new file with the given content, creating any missing parent folders. */
public class CreateFileTool implements McpTool {

    @Override
    public String name() {
        return "create_file";
    }

    @Override
    public String description() {
        return "Create a new file with the given content in a project. Fails if the file already exists. Any "
                + "missing parent folders are created automatically.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to create the file in");
        Schemas.prop(schema, "filePath", "string", "Path of the new file, relative to the project root");
        Schemas.prop(schema, "content", "string", "The content to write to the file");
        return Schemas.required(schema, "projectName", "filePath", "content");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        String content = Schemas.requireString(arguments, "content");

        IProject project = CoderResources.project(projectName);
        IPath path = new Path(filePath);
        IFile file = project.getFile(path);
        if (file.exists()) {
            throw new IllegalArgumentException("File already exists: " + filePath);
        }

        for (int i = path.segmentCount() - 1; i >= 1; i--) {
            org.eclipse.core.resources.IFolder folder = project.getFolder(path.removeLastSegments(i));
            if (!folder.exists()) {
                folder.create(true, true, new NullProgressMonitor());
            }
        }

        file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true,
                new NullProgressMonitor());
        return "Created " + filePath;
    }
}
