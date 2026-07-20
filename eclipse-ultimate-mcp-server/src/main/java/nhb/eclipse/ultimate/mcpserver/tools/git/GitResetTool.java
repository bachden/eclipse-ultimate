package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Resets the current branch, index or working tree to a ref (equivalent to `git reset`). */
public class GitResetTool implements McpTool {

    @Override
    public String name() {
        return "git_reset";
    }

    @Override
    public String description() {
        return "Reset the current branch to a ref. mode is SOFT (move HEAD only), MIXED (default; also reset the "
                + "index) or HARD (also overwrite the working tree — discards uncommitted changes).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project whose repository to reset");
        Schemas.prop(schema, "ref", "string", "Ref/commit to reset to (default HEAD)");
        Schemas.prop(schema, "mode", "string", "SOFT, MIXED or HARD (default MIXED)");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String ref = Schemas.optString(arguments, "ref", "HEAD");
        String mode = Schemas.optString(arguments, "mode", "MIXED").toUpperCase();

        try (Git git = GitRepos.git(projectName)) {
            git.reset().setRef(ref).setMode(ResetCommand.ResetType.valueOf(mode)).call();
            return "Reset (" + mode + ") to " + ref;
        }
    }
}
