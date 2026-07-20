package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import java.util.function.Consumer;

import org.eclipse.core.runtime.Platform;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;

/**
 * Registers Mylyn-backed tools only when the corresponding Mylyn bundles exist.
 */
public final class MylynTools {

    private MylynTools() {
    }

    public static void registerAll(Consumer<McpTool> register) {
        if (Platform.getBundle("org.eclipse.mylyn.tasks.ui") != null) {
            register.accept(new ListMylynTaskRepositoriesTool());
            register.accept(new GetMylynTaskRepositoryTool());
        }
        if (Platform.getBundle("org.eclipse.mylyn.builds.ui") != null) {
            register.accept(new ListMylynBuildServersTool());
            register.accept(new GetMylynBuildServerTool());
            register.accept(new GetMylynBuildLogTool());
        }
    }
}
