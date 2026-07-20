package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Overwrites a file's entire content. */
public class ReplaceFileContentTool implements McpTool {

    @Override
    public String name() {
        return "replace_file_content";
    }

    @Override
    public String description() {
        return "Overwrite a file's entire content through Eclipse's shared text buffer. The file must already exist; "
                + "use create_file for new files.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "content", "string", "New content for the file");
        return Schemas.required(schema, "projectName", "filePath", "content");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        String content = Schemas.requireString(arguments, "content");

        IFile file = TextFiles.file(projectName, filePath);
        TextFiles.EditResult edit = TextFiles.write(file, content);
        return edit.changed() ? "Replaced content of " + filePath : "Content already matched " + filePath;
    }
}
