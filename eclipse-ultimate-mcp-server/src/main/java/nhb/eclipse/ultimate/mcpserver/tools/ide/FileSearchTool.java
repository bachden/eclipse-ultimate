package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Plain substring search across text files in a project (case-insensitive). */
public class FileSearchTool implements McpTool {

    @Override
    public String name() {
        return "file_search";
    }

    @Override
    public String description() {
        return "Search for a plain-text substring across all files in a project (case-insensitive). Returns "
                + "matching file paths with line numbers and the matching line text.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to search in");
        Schemas.prop(schema, "query", "string", "Plain-text substring to search for");
        Schemas.prop(schema, "maxResults", "integer", "Maximum number of matches to return (default 200)");
        return Schemas.required(schema, "projectName", "query");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String query = Schemas.requireString(arguments, "query");
        int maxResults = Schemas.optInt(arguments, "maxResults", 200);

        IProject project = Workspace.project(projectName);
        JsonArray matches = new JsonArray();
        List<IFile> files = FileWalker.allFiles(project);
        String needle = query.toLowerCase();
        outer: for (IFile file : files) {
            if (!FileWalker.isProbablyText(file)) {
                continue;
            }
            List<String> lines = FileWalker.readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.toLowerCase().contains(needle)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("path", file.getProjectRelativePath().toString());
                    entry.addProperty("line", i + 1);
                    entry.addProperty("text", line.strip());
                    matches.add(entry);
                    if (matches.size() >= maxResults) {
                        break outer;
                    }
                }
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(matches);
    }
}
