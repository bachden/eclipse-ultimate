package nhb.eclipse.plugin.mcp.ultimate.tools.coder;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.plugin.mcp.ultimate.mcp.McpTool;
import nhb.eclipse.plugin.mcp.ultimate.tools.Schemas;

/** Inserts text at a given 1-based line number in a file (pushing existing content down). */
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
        String content = TextFiles.read(file);
        String[] lines = content.split("\n", -1);
        boolean trailingNewline = content.endsWith("\n");
        int lineCount = trailingNewline ? lines.length - 1 : lines.length;
        if (line < 1 || line > lineCount + 1) {
            throw new IllegalArgumentException("line " + line + " out of range (file has " + lineCount + " lines)");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            if (i + 1 == line) {
                sb.append(text);
            }
            sb.append(lines[i]).append('\n');
        }
        if (line == lineCount + 1) {
            sb.append(text);
        }

        TextFiles.write(file, sb.toString());
        return "Inserted at line " + line + " of " + filePath;
    }
}
