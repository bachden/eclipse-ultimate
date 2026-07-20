package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Reads the text content of a single file inside a project. */
public class ReadProjectResourceTool implements McpTool {

    @Override
    public String name() {
        return "read_project_resource";
    }

    @Override
    public String description() {
        return "Read the text content of a file inside a project, given a path relative to the project root.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The name of the project containing the file");
        Schemas.prop(schema, "resourcePath", "string", "Path to the file, relative to the project root");
        return Schemas.required(schema, "projectName", "resourcePath");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String resourcePath = Schemas.requireString(arguments, "resourcePath");

        IProject project = Workspace.project(projectName);
        IResource resource = Workspace.resource(project, resourcePath);
        if (!(resource instanceof IFile)) {
            throw new IllegalArgumentException(resourcePath + " is not a file");
        }
        IFile file = (IFile) resource;
        try (InputStream in = file.getContents()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
