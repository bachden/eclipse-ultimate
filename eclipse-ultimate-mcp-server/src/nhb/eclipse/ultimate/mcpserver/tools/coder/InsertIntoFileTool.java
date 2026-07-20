package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Inserts text at a given 1-based line number in a file (pushing existing
 * content down).
 */
public class InsertIntoFileTool implements McpTool {

    @Override
    public String name() {
        return "insert_into_file";
    }

    @Override
    public String description() {
        return "Insert text before a given 1-based line number in a file. Line 1 inserts at the top; a line "
                + "number one past the last line appends at the end.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "line", "integer", "1-based line number to insert before");
        Schemas.prop(schema, "text", "string", "Text to insert (should end with a newline if inserting whole lines)");
        return Schemas.required(schema, "projectName", "filePath", "line", "text");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        int line = Schemas.optInt(arguments, "line", 1);
        String text = Schemas.requireString(arguments, "text");

        IFile file = TextFiles.file(projectName, filePath);
        TextFiles.EditResult edit = TextFiles.edit(file, content -> {
            String[] lines = content.split("\n", -1);
            boolean trailingNewline = content.endsWith("\n");
            int lineCount = trailingNewline ? lines.length - 1 : lines.length;
            if (line < 1 || line > lineCount + 1) {
                throw new IllegalArgumentException("line " + line + " out of range (file has " + lineCount + " lines)");
            }

            StringBuilder updated = new StringBuilder();
            for (int i = 0; i < lineCount; i++) {
                if (i + 1 == line) {
                    updated.append(text);
                }
                updated.append(lines[i]).append('\n');
            }
            if (line == lineCount + 1) {
                updated.append(text);
            }
            return updated.toString();
        });
        return edit.changed() ? "Inserted at line " + line + " of " + filePath
                : "Insertion made no content change in " + filePath;
    }
}
