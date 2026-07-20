package nhb.eclipse.ultimate.mcpserver.tools.git;

import java.util.function.Consumer;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;

/** Registers every Git tool, backed by EGit's repository cache + JGit's porcelain API. */
public final class GitTools {

    private GitTools() {
    }

    public static void registerAll(Consumer<McpTool> register) {
        register.accept(new GitStatusTool());
        register.accept(new GitDiffTool());
        register.accept(new GitLogTool());
        register.accept(new GitAddTool());
        register.accept(new GitCommitTool());
        register.accept(new GitResetTool());
        register.accept(new GitBranchTool());
        register.accept(new GitCreateBranchTool());
        register.accept(new GitCheckoutTool());
        register.accept(new GitDeleteBranchTool());
        register.accept(new GitStashTool());
        register.accept(new GitStashListTool());
        register.accept(new GitStashPopTool());
    }
}
