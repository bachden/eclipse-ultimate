package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.ui.console.IConsole;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsoleManager;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Returns the currently buffered console output of a running or finished launch's process.
 *
 * <p>Reads from the actual {@link IConsole}'s document — the same text the Console view
 * displays — rather than {@code IStreamMonitor.getContents()}, which only buffers output
 * produced after a listener is attached and misses everything printed before this tool is first
 * called on a given process.
 */
public class GetConsoleOutputTool implements McpTool {

    @Override
    public String name() {
        return "get_console_output";
    }

    @Override
    public String description() {
        return "Get the currently buffered stdout/stderr of a launched process, given its launch label as shown "
                + "by list_active_launches. Returns the most recently launched matching process's output.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "launchLabel", "string",
                "Label of the launch to read output from (substring match against the launch configuration name)");
        return Schemas.required(schema, "launchLabel");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String launchLabel = Schemas.requireString(arguments, "launchLabel");

        ILaunch[] launches = DebugPlugin.getDefault().getLaunchManager().getLaunches();
        for (int i = launches.length - 1; i >= 0; i--) {
            ILaunch launch = launches[i];
            String name = launch.getLaunchConfiguration() != null ? launch.getLaunchConfiguration().getName() : "";
            if (!name.contains(launchLabel)) {
                continue;
            }
            for (IProcess process : launch.getProcesses()) {
                IConsole console = findConsole(process);
                if (console != null) {
                    String text = console.getDocument().get();
                    return text.isEmpty() ? "(no output captured yet)" : text;
                }
            }
            return "(no console found for this launch's process — it may not have started yet)";
        }
        throw new IllegalArgumentException("No launch found matching: " + launchLabel);
    }

    private IConsole findConsole(IProcess process) {
        IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
        for (org.eclipse.ui.console.IConsole candidate : manager.getConsoles()) {
            if (candidate instanceof IConsole debugConsole && process.equals(debugConsole.getProcess())) {
                return debugConsole;
            }
        }
        return null;
    }
}
