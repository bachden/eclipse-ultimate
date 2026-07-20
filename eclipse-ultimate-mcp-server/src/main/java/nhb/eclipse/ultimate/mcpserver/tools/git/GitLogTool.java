package nhb.eclipse.ultimate.mcpserver.tools.git;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists recent commits reachable from HEAD. */
public class GitLogTool implements McpTool {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_INSTANT;

    @Override
    public String name() {
        return "git_log";
    }

    @Override
    public String description() {
        return "List recent commits reachable from HEAD: hash, author, date and message summary.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to inspect");
        Schemas.prop(schema, "maxCount", "integer", "Maximum number of commits to return (default 20)");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        int maxCount = Schemas.optInt(arguments, "maxCount", 20);

        try (Git git = GitRepos.git(projectName)) {
            JsonArray commits = new JsonArray();
            for (RevCommit commit : git.log().setMaxCount(maxCount).call()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("hash", commit.getName());
                entry.addProperty("author", commit.getAuthorIdent().getName());
                entry.addProperty("date", TIME_FORMAT.format(Instant.ofEpochSecond(commit.getCommitTime())));
                entry.addProperty("message", commit.getShortMessage());
                commits.add(entry);
            }
            return new GsonBuilder().setPrettyPrinting().create().toJson(commits);
        }
    }
}
