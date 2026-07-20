package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists all stash entries. */
public class GitStashListTool implements McpTool {

    @Override
    public String name() {
        return "git_stash_list";
    }

    @Override
    public String description() {
        return "List all stash entries in a project's Git repository, most recent first.";
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
            JsonArray stashes = new JsonArray();
            int index = 0;
            for (RevCommit stash : git.stashList().call()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("index", index++);
                entry.addProperty("hash", stash.getName());
                entry.addProperty("message", stash.getShortMessage());
                stashes.add(entry);
            }
            return new GsonBuilder().setPrettyPrinting().create().toJson(stashes);
        }
    }
}
