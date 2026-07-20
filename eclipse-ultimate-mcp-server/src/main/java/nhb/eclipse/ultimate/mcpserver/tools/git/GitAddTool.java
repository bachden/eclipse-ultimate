package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Stages one or more paths (equivalent to `git add`). */
public class GitAddTool implements McpTool {

    @Override
    public String name() {
        return "git_add";
    }

    @Override
    public String description() {
        return "Stage one or more paths in a project's Git repository (git add). Pass '.' to stage everything.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to stage in");
        Schemas.prop(schema, "path", "string", "Path (relative to the repository root) to stage; '.' stages everything");
        return Schemas.required(schema, "projectName", "path");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String path = Schemas.requireString(arguments, "path");

        try (Git git = GitRepos.git(projectName)) {
            git.add().addFilepattern(path).call();
            return "Staged " + path;
        }
    }
}
