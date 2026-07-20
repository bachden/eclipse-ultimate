package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Checks out a branch, tag or commit. */
public class GitCheckoutTool implements McpTool {

    @Override
    public String name() {
        return "git_checkout";
    }

    @Override
    public String description() {
        return "Check out a branch, tag or commit in a project's Git repository. Set createBranch=true to create "
                + "and check out a new branch in one step.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to check out in");
        Schemas.prop(schema, "ref", "string", "Branch name, tag or commit to check out");
        Schemas.prop(schema, "createBranch", "boolean", "Create ref as a new branch instead of checking out an existing one");
        return Schemas.required(schema, "projectName", "ref");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String ref = Schemas.requireString(arguments, "ref");
        boolean createBranch = Schemas.optBoolean(arguments, "createBranch", false);

        try (Git git = GitRepos.git(projectName)) {
            git.checkout().setName(ref).setCreateBranch(createBranch).call();
            return "Checked out " + ref;
        }
    }
}
