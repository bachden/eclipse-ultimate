package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists local branches, marking the current one. */
public class GitBranchTool implements McpTool {

    @Override
    public String name() {
        return "git_branch";
    }

    @Override
    public String description() {
        return "List local branches in a project's Git repository, marking the currently checked-out one.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to inspect");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");

        try (Git git = GitRepos.git(projectName)) {
            String current = git.getRepository().getBranch();
            JsonArray branches = new JsonArray();
            for (Ref ref : git.branchList().call()) {
                String name = ref.getName().replaceFirst("^refs/heads/", "");
                JsonObject entry = new JsonObject();
                entry.addProperty("name", name);
                entry.addProperty("current", name.equals(current));
                branches.add(entry);
            }
            return new GsonBuilder().setPrettyPrinting().create().toJson(branches);
        }
    }
}
