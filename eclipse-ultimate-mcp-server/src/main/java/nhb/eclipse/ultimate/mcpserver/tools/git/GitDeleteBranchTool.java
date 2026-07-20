package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Deletes a local branch. */
public class GitDeleteBranchTool implements McpTool {

    @Override
    public String name() {
        return "git_delete_branch";
    }

    @Override
    public String description() {
        return "Delete a local branch. Set force=true to delete even if it is not fully merged.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to modify");
        Schemas.prop(schema, "branchName", "string", "Branch to delete");
        Schemas.prop(schema, "force", "boolean", "Delete even if not fully merged");
        return Schemas.required(schema, "projectName", "branchName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String branchName = Schemas.requireString(arguments, "branchName");
        boolean force = Schemas.optBoolean(arguments, "force", false);

        try (Git git = GitRepos.git(projectName)) {
            git.branchDelete().setBranchNames(branchName).setForce(force).call();
            return "Deleted branch " + branchName;
        }
    }
}
