package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Replaces an exact string match in a file, requiring the match to be unique
 * unless replaceAll is set.
 */
public class ReplaceStringTool implements McpTool {

    @Override
    public String name() {
        return "replace_string";
    }

    @Override
    public String description() {
        return "Replace an exact string match in a file. Fails if oldString is not found, or if it matches more "
                + "than once and replaceAll is not set.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "oldString", "string", "Exact text to find");
        Schemas.prop(schema, "newString", "string", "Replacement text");
        Schemas.prop(schema, "replaceAll", "boolean", "Replace every occurrence instead of requiring a unique match");
        return Schemas.required(schema, "projectName", "filePath", "oldString", "newString");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        String oldString = Schemas.requireString(arguments, "oldString");
        String newString = Schemas.requireString(arguments, "newString");
        boolean replaceAll = Schemas.optBoolean(arguments, "replaceAll", false);

        IFile file = TextFiles.file(projectName, filePath);
        TextFiles.EditResult edit = TextFiles.edit(file, content -> {
            int firstIndex = content.indexOf(oldString);
            if (firstIndex < 0) {
                throw new IllegalArgumentException("oldString not found in " + filePath);
            }
            if (!replaceAll) {
                int secondIndex = content.indexOf(oldString, firstIndex + oldString.length());
                if (secondIndex >= 0) {
                    throw new IllegalArgumentException("oldString matches multiple locations in " + filePath
                            + "; use replaceAll or a more specific match");
                }
            }
            return replaceAll ? content.replace(oldString, newString)
                    : content.substring(0, firstIndex) + newString + content.substring(firstIndex + oldString.length());
        });
        return edit.changed() ? "Updated " + filePath : "No content change needed in " + filePath;
    }
}
