package nhb.eclipse.ultimate.mcpserver.tools.git;

import java.io.ByteArrayOutputStream;

import org.eclipse.jgit.api.Git;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns a unified diff of working-tree/staged changes, optionally limited to one path. */
public class GitDiffTool implements McpTool {

    @Override
    public String name() {
        return "git_diff";
    }

    @Override
    public String description() {
        return "Get a unified diff for a project's Git repository. By default diffs the working tree against "
                + "the index (unstaged changes); set staged=true to diff the index against HEAD instead.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to diff");
        Schemas.prop(schema, "path", "string", "Optional path (relative to the repository root) to limit the diff to");
        Schemas.prop(schema, "staged", "boolean", "Diff the index against HEAD instead of the working tree against the index");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String path = Schemas.optString(arguments, "path", null);
        boolean staged = Schemas.optBoolean(arguments, "staged", false);

        try (Git git = GitRepos.git(projectName)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            org.eclipse.jgit.api.DiffCommand diff = git.diff().setCached(staged).setOutputStream(out);
            if (path != null) {
                diff.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(path));
            }
            diff.call();
            String result = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            return result.isEmpty() ? "(no changes)" : result;
        }
    }
}
