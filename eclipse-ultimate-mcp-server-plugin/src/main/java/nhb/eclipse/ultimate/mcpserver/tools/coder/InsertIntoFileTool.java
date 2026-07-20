package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Inserts text at a 1-based line number, optionally replacing a line range.
 */
public class InsertIntoFileTool implements McpTool {

    @Override
    public String name() {
        return "insert_into_file";
    }

    @Override
    public String description() {
        return "Insert text before a given 1-based line number, or replace an inclusive line range when "
                + "replaceEndLine is provided. Line 1 is the top; one past the last line appends.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "line", "integer", "1-based insertion line or first line to replace");
        Schemas.prop(schema, "replaceEndLine", "integer",
                "Optional 1-based last line to replace (inclusive); must be at or after line");
        Schemas.prop(schema, "text", "string",
                "Text to insert or use as the replacement; include a line delimiter when needed");
        return Schemas.required(schema, "projectName", "filePath", "line", "text");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        int line = Schemas.optInt(arguments, "line", 1);
        Integer replaceEndLine = arguments.has("replaceEndLine") && !arguments.get("replaceEndLine").isJsonNull()
                ? arguments.get("replaceEndLine").getAsInt()
                : null;
        String text = Schemas.requireString(arguments, "text");

        IFile file = TextFiles.file(projectName, filePath);
        TextFiles.EditResult edit = TextFiles.edit(file, content -> updateContent(content, line, replaceEndLine, text));
        if (!edit.changed()) {
            return "Edit made no content change in " + filePath;
        }
        if (replaceEndLine != null) {
            return "Replaced lines " + line + ".." + replaceEndLine + " in " + filePath;
        }
        return "Inserted at line " + line + " of " + filePath;
    }

    private static String updateContent(String content, int line, Integer replaceEndLine, String text)
            throws Exception {
        IDocument document = new Document(content);
        int lineCount = document.getNumberOfLines();
        int insertionLimit = lineCount + 1;

        if (line < 1 || line > insertionLimit) {
            throw new IllegalArgumentException("line " + line + " out of range (file has " + lineCount + " lines)");
        }
        if (replaceEndLine != null && (line > lineCount || replaceEndLine < line || replaceEndLine > lineCount)) {
            throw new IllegalArgumentException("Invalid replacement range " + line + ".." + replaceEndLine
                    + " (file has " + lineCount + " lines)");
        }

        int startOffset = line == insertionLimit ? content.length() : document.getLineOffset(line - 1);
        int replacedLength = 0;
        if (replaceEndLine != null) {
            int endIndex = replaceEndLine - 1;
            replacedLength = document.getLineOffset(endIndex) + document.getLineLength(endIndex) - startOffset;
        }
        return content.substring(0, startOffset) + text + content.substring(startOffset + replacedLength);
    }
}
