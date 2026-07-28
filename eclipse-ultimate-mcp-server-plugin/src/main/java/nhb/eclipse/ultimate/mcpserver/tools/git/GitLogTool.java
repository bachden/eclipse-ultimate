package nhb.eclipse.ultimate.mcpserver.tools.git;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists recent commits reachable from HEAD, optionally limited to commits touching a subset of paths. */
public class GitLogTool implements McpTool {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_INSTANT;

    @Override
    public String name() {
        return "git_log";
    }

    @Override
    public String description() {
        return "List recent commits reachable from HEAD: hash, author, date and message summary. "
                + "Use paths to only include commits that touch a subset of files/directories.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to inspect");
        Schemas.prop(schema, "maxCount", "integer", "Maximum number of commits to return (default 20)");
        Schemas.prop(schema, "path", "string", "Optional single path (relative to the repository root) to filter commits by");
        Schemas.stringArrayProp(schema, "paths",
                "Optional list of paths (relative to the repository root) to filter commits by; combined with path if both are given");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        int maxCount = Schemas.optInt(arguments, "maxCount", 20);
        String path = Schemas.optString(arguments, "path", null);
        Set<String> paths = new LinkedHashSet<>(Schemas.optStringSet(arguments, "paths"));
        if (path != null) {
            paths.add(path);
        }

        try (Git git = GitRepos.git(projectName)) {
            LogCommand log = git.log().setMaxCount(maxCount);
            for (String p : paths) {
                log.addPath(p);
            }

            JsonArray commits = new JsonArray();
            for (RevCommit commit : log.call()) {
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
