package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Finds files whose project-relative path matches a glob-like substring/suffix pattern. */
public class FindFilesTool implements McpTool {

    @Override
    public String name() {
        return "find_files";
    }

    @Override
    public String description() {
        return "Find files in a project whose relative path contains or ends with the given pattern "
                + "(simple substring/suffix match, e.g. '.java' or 'PgSessionRepo').";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to search in");
        Schemas.prop(schema, "pattern", "string", "Substring/suffix to match against the file's relative path");
        Schemas.prop(schema, "maxResults", "integer", "Maximum number of results to return (default 200)");
        return Schemas.required(schema, "projectName", "pattern");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String pattern = Schemas.requireString(arguments, "pattern");
        int maxResults = Schemas.optInt(arguments, "maxResults", 200);

        IProject project = Workspace.project(projectName);
        JsonArray results = new JsonArray();
        List<IFile> files = FileWalker.allFiles(project);
        for (IFile file : files) {
            String relative = file.getProjectRelativePath().toString();
            if (relative.contains(pattern)) {
                results.add(relative);
                if (results.size() >= maxResults) {
                    break;
                }
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
