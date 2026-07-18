package nhb.eclipse.plugin.mcp.ultimate.tools.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.gson.JsonObject;

import nhb.eclipse.plugin.mcp.ultimate.mcp.McpTool;
import nhb.eclipse.plugin.mcp.ultimate.tools.Schemas;

/** Stashes uncommitted changes (equivalent to `git stash push`). */
public class GitStashTool implements McpTool {

    @Override
    public String name() {
        return "git_stash";
    }

    @Override
    public String description() {
        return "Stash uncommitted changes in a project's Git repository (git stash push). Set includeUntracked "
                + "to also stash untracked files.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to stash in");
        Schemas.prop(schema, "message", "string", "Optional stash message");
        Schemas.prop(schema, "includeUntracked", "boolean", "Also stash untracked files");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String message = Schemas.optString(arguments, "message", null);
        boolean includeUntracked = Schemas.optBoolean(arguments, "includeUntracked", false);

        try (Git git = GitRepos.git(projectName)) {
            org.eclipse.jgit.api.StashCreateCommand cmd = git.stashCreate().setIncludeUntracked(includeUntracked);
            if (message != null) {
                cmd.setWorkingDirectoryMessage(message);
            }
            RevCommit stash = cmd.call();
            return stash == null ? "Nothing to stash" : "Stashed as " + stash.getName();
        }
    }
}
