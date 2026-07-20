package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Creates a new local branch, optionally checking it out. */
public class GitCreateBranchTool implements McpTool {

    @Override
    public String name() {
        return "git_create_branch";
    }

    @Override
    public String description() {
        return "Create a new local branch from the given start point (default HEAD). Does not check it out; use "
                + "git_checkout for that.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to branch");
        Schemas.prop(schema, "branchName", "string", "Name for the new branch");
        Schemas.prop(schema, "startPoint", "string", "Ref/commit to branch from (default HEAD)");
        return Schemas.required(schema, "projectName", "branchName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String branchName = Schemas.requireString(arguments, "branchName");
        String startPoint = Schemas.optString(arguments, "startPoint", "HEAD");

        try (Git git = GitRepos.git(projectName)) {
            git.branchCreate().setName(branchName).setStartPoint(startPoint).call();
            return "Created branch " + branchName + " from " + startPoint;
        }
    }
}
