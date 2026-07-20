package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Commits currently staged changes. */
public class GitCommitTool implements McpTool {

    @Override
    public String name() {
        return "git_commit";
    }

    @Override
    public String description() {
        return "Commit currently staged changes in a project's Git repository. Fails with JGit's usual error if "
                + "nothing is staged.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to commit in");
        Schemas.prop(schema, "message", "string", "Commit message");
        Schemas.prop(schema, "amend", "boolean", "Amend the previous commit instead of creating a new one");
        return Schemas.required(schema, "projectName", "message");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String message = Schemas.requireString(arguments, "message");
        boolean amend = Schemas.optBoolean(arguments, "amend", false);

        try (Git git = GitRepos.git(projectName)) {
            RevCommit commit = git.commit().setMessage(message).setAmend(amend).call();
            return "Committed " + commit.getName();
        }
    }
}
