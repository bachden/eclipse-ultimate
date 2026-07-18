package nhb.eclipse.plugin.mcp.ultimate.tools.coder;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.plugin.mcp.ultimate.mcp.McpTool;
import nhb.eclipse.plugin.mcp.ultimate.tools.Schemas;

/** Deletes an inclusive 1-based line range from a file. */
public class DeleteLinesInFileTool implements McpTool {

    @Override
    public String name() {
        return "delete_lines_in_file";
    }

    @Override
    public String description() {
        return "Delete an inclusive range of 1-based lines from a file.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "startLine", "integer", "1-based first line to delete (inclusive)");
        Schemas.prop(schema, "endLine", "integer", "1-based last line to delete (inclusive)");
        return Schemas.required(schema, "projectName", "filePath", "startLine", "endLine");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        int startLine = Schemas.optInt(arguments, "startLine", 1);
        int endLine = Schemas.optInt(arguments, "endLine", startLine);

        IFile file = TextFiles.file(projectName, filePath);
        String content = TextFiles.read(file);
        String[] lines = content.split("\n", -1);
        boolean trailingNewline = content.endsWith("\n");
        int lineCount = trailingNewline ? lines.length - 1 : lines.length;
        if (startLine < 1 || endLine < startLine || endLine > lineCount) {
            throw new IllegalArgumentException(
                    "Invalid range " + startLine + ".." + endLine + " (file has " + lineCount + " lines)");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            int lineNo = i + 1;
            if (lineNo >= startLine && lineNo <= endLine) {
                continue;
            }
            sb.append(lines[i]).append('\n');
        }

        TextFiles.write(file, sb.toString());
        return "Deleted lines " + startLine + ".." + endLine + " from " + filePath;
    }
}
