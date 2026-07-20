package nhb.eclipse.ultimate.mcpserver.tools.runner;

import java.util.function.Consumer;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;

/** Registers every Runner/Debug tool, backed by the Debug platform's launch manager. */
public final class RunnerTools {

    private RunnerTools() {
    }

    public static void registerAll(Consumer<McpTool> register) {
        register.accept(new ListLaunchConfigurationsTool());
        register.accept(new GetLaunchConfigurationTool());
        register.accept(new CreateLaunchConfigurationTool());
        register.accept(new UpdateLaunchConfigurationTool());
        register.accept(new RefreshLaunchConfigurationsTool());
        register.accept(new LaunchConfigurationTool());
        register.accept(new ListActiveLaunchesTool());
        register.accept(new StopApplicationTool());
        register.accept(new ListBreakpointsTool());
        register.accept(new ToggleBreakpointTool());
        register.accept(new SetConditionalBreakpointTool());
        register.accept(new RemoveAllBreakpointsTool());
        register.accept(new ResumeDebugTool());
        register.accept(new StepIntoTool());
        register.accept(new StepOverTool());
        register.accept(new StepReturnTool());
        register.accept(new GetStackTraceTool());
        register.accept(new EvaluateExpressionTool());
    }
}
