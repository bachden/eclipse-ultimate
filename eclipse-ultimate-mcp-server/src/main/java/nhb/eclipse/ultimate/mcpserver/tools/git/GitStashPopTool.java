package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Applies and drops the most recent (or a specific) stash entry. */
public class GitStashPopTool implements McpTool {

    @Override
    public String name() {
        return "git_stash_pop";
    }

    @Override
    public String description() {
        return "Apply and drop a stash entry (git stash pop). Defaults to the most recent (index 0).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to pop from");
        Schemas.prop(schema, "stashRef", "integer", "Stash index to pop (default 0, the most recent)");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        int stashRef = Schemas.optInt(arguments, "stashRef", 0);

        try (Git git = GitRepos.git(projectName)) {
            git.stashApply().setStashRef("stash@{" + stashRef + "}").call();
            git.stashDrop().setStashRef(stashRef).call();
            return "Popped stash@{" + stashRef + "}";
        }
    }
}
