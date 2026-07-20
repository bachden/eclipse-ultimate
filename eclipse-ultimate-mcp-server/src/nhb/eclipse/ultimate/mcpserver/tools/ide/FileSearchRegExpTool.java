package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Regular-expression search across text files in a project. */
public class FileSearchRegExpTool implements McpTool {

    @Override
    public String name() {
        return "file_search_regexp";
    }

    @Override
    public String description() {
        return "Search for a Java regular expression across all files in a project. Returns matching file paths "
                + "with line numbers and the matching line text.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to search in");
        Schemas.prop(schema, "pattern", "string", "Java regular expression to search for (applied per line)");
        Schemas.prop(schema, "maxResults", "integer", "Maximum number of matches to return (default 200)");
        return Schemas.required(schema, "projectName", "pattern");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String patternText = Schemas.requireString(arguments, "pattern");
        int maxResults = Schemas.optInt(arguments, "maxResults", 200);

        Pattern pattern = Pattern.compile(patternText);
        IProject project = Workspace.project(projectName);
        JsonArray matches = new JsonArray();
        List<IFile> files = FileWalker.allFiles(project);
        outer: for (IFile file : files) {
            if (!FileWalker.isProbablyText(file)) {
                continue;
            }
            List<String> lines = FileWalker.readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
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
