package nhb.eclipse.ultimate.mcpserver.tools.git;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns a unified diff of working-tree/staged changes, optionally limited to a subset of paths. */
public class GitDiffTool implements McpTool {

    @Override
    public String name() {
        return "git_diff";
    }

    @Override
    public String description() {
        return "Get a unified diff for a project's Git repository. By default diffs the working tree against "
                + "the index (unstaged changes); set staged=true to diff the index against HEAD instead. "
                + "Use paths to limit the diff to a subset of files/directories.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to diff");
        Schemas.prop(schema, "path", "string", "Optional single path (relative to the repository root) to limit the diff to");
        Schemas.stringArrayProp(schema, "paths",
                "Optional list of paths (relative to the repository root) to limit the diff to; combined with path if both are given");
        Schemas.prop(schema, "staged", "boolean", "Diff the index against HEAD instead of the working tree against the index");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String path = Schemas.optString(arguments, "path", null);
        Set<String> paths = new LinkedHashSet<>(Schemas.optStringSet(arguments, "paths"));
        if (path != null) {
            paths.add(path);
        }
        boolean staged = Schemas.optBoolean(arguments, "staged", false);

        try (Git git = GitRepos.git(projectName)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            org.eclipse.jgit.api.DiffCommand diff = git.diff().setCached(staged).setOutputStream(out);
            if (!paths.isEmpty()) {
                diff.setPathFilter(PathFilterGroup.createFromStrings(paths));
            }
            diff.call();
            String result = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            return result.isEmpty() ? "(no changes)" : result;
        }
    }
}
