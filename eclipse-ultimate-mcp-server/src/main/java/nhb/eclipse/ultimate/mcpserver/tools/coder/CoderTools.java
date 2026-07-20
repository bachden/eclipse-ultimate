package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.function.Consumer;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;

/** Registers every Coder tool (file creation/editing, refactoring). */
public final class CoderTools {

    private CoderTools() {
    }

    public static void registerAll(Consumer<McpTool> register) {
        register.accept(new CreateFileTool());
        register.accept(new CreateDirectoriesTool());
        register.accept(new DeleteFileTool());
        register.accept(new RenameFileTool());
        register.accept(new MoveResourceTool());
        register.accept(new ReplaceStringTool());
        register.accept(new ReplaceFileContentTool());
        register.accept(new InsertIntoFileTool());
        register.accept(new DeleteLinesInFileTool());
        register.accept(new ApplyPatchTool());
        register.accept(new OrganizeImportsTool());
        register.accept(new QuickFixTool());
        register.accept(new CleanUpTool());
        register.accept(new RefactorRenameJavaTypeTool());
        register.accept(new RefactorMoveJavaTypeTool());
        register.accept(new RefactorRenamePackageTool());
    }
}
