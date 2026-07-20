package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.function.Consumer;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;

/**
 * Registers every Core IDE tool (project browsing, search, JDT introspection,
 * tests, Maven).
 */
public final class IdeTools {

    private IdeTools() {
    }

    public static void registerAll(Consumer<McpTool> register) {
        register.accept(new ListProjectsTool());
        register.accept(new GetProjectDetailsTool());
        register.accept(new GetProjectLayoutTool());
        register.accept(new ReadProjectResourceTool());
        register.accept(new ResolveWorkspaceFileTool());
        register.accept(new FileSearchTool());
        register.accept(new FileSearchRegExpTool());
        register.accept(new FindFilesTool());
        register.accept(new GetSourceTool());
        register.accept(new GetMethodSourceTool());
        register.accept(new GetClassOutlineTool());
        register.accept(new FindReferencesTool());
        register.accept(new GetCompilationErrorsTool());
        register.accept(new FormatCodeTool());
        register.accept(new GetCurrentlyOpenedFileTool());
        register.accept(new OpenFileTool());
        register.accept(new ShowProposedChangesTool());
        register.accept(new RunAllTestsTool());
        register.accept(new RunClassTestsTool());
        register.accept(new FindTestClassesTool());
        register.accept(new GetProjectDependenciesTool());
        register.accept(new GetConsoleOutputTool());
        register.accept(new CheckForUpdatesTool());
    }
}
