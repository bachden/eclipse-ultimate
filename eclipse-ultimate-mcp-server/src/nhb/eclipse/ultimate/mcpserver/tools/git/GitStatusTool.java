package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Reports the working tree/index status of a project's Git repository (equivalent to `git status`). */
public class GitStatusTool implements McpTool {

    @Override
    public String name() {
        return "git_status";
    }

    @Override
    public String description() {
        return "Get the working tree and index status of a project's Git repository: staged, modified, "
                + "untracked, conflicting and missing files, plus the current branch.";
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
            Status status = git.status().call();
            JsonObject result = new JsonObject();
            result.addProperty("branch", git.getRepository().getBranch());
            result.add("added", toArray(status.getAdded()));
            result.add("changed", toArray(status.getChanged()));
            result.add("modified", toArray(status.getModified()));
            result.add("removed", toArray(status.getRemoved()));
            result.add("missing", toArray(status.getMissing()));
            result.add("untracked", toArray(status.getUntracked()));
            result.add("conflicting", toArray(status.getConflicting()));
            return new GsonBuilder().setPrettyPrinting().create().toJson(result);
        }
    }

    private JsonArray toArray(java.util.Set<String> paths) {
        JsonArray array = new JsonArray();
        for (String path : paths) {
            array.add(path);
        }
        return array;
    }
}
