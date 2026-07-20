package nhb.eclipse.ultimate.mcpserver.tools.maven;

import java.util.function.Consumer;

import org.eclipse.core.runtime.Platform;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;

/** Registers Maven tools when m2e is installed in the running Eclipse. */
public final class MavenTools {

    private MavenTools() {
    }

    public static void registerAll(Consumer<McpTool> register) {
        if (Platform.getBundle("org.eclipse.m2e.core") == null) {
            return;
        }
        register.accept(new GetMavenDependencyGraphTool());
        register.accept(new ResolveMavenDependencyTool());
        register.accept(new OpenMavenDependencyProjectTool());
        register.accept(new RefreshMavenProjectTool());
        if (Platform.getBundle("org.eclipse.m2e.core.ui") != null) {
            register.accept(new ConfigureMavenDependencyTool());
            register.accept(new RemoveMavenDependencyTool());
        }
    }
}
