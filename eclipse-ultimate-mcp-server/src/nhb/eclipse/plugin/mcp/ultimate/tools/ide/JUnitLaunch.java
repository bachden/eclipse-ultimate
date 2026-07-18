package nhb.eclipse.plugin.mcp.ultimate.tools.ide;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Runs JUnit tests via a synthetic {@code org.eclipse.jdt.junit.launchconfig}
 * launch configuration and blocks until the launched process terminates,
 * capturing combined stdout/stderr. Good enough for MCP tool calls, which are
 * synchronous request/response by nature.
 */
final class JUnitLaunch {

    private static final String JUNIT_LAUNCH_TYPE = "org.eclipse.jdt.junit.launchconfig";

    private JUnitLaunch() {
    }

    /** Runs every test in a project. */
    static String runProject(String projectName, long timeoutMillis) throws Exception {
        IJavaProject javaProject = Workspace.javaProject(projectName);
        ILaunchConfigurationWorkingCopy wc = newConfig(javaProject.getElementName() + " (all tests)");
        wc.setAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, projectName);
        wc.setAttribute("org.eclipse.jdt.junit.CONTAINER", javaProject.getHandleIdentifier());
        wc.setAttribute("org.eclipse.jdt.junit.TESTKIND", "org.eclipse.jdt.junit.loader.junit4");
        return launchAndCapture(wc, timeoutMillis);
    }

    /** Runs every test method in a single test class. */
    static String runClass(String projectName, String fqTestClassName, long timeoutMillis) throws Exception {
        ILaunchConfigurationWorkingCopy wc = newConfig(fqTestClassName + " (class)");
        wc.setAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, projectName);
        wc.setAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, fqTestClassName);
        wc.setAttribute("org.eclipse.jdt.junit.TESTKIND", "org.eclipse.jdt.junit.loader.junit4");
        return launchAndCapture(wc, timeoutMillis);
    }

    private static ILaunchConfigurationWorkingCopy newConfig(String name) throws Exception {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = manager.getLaunchConfigurationType(JUNIT_LAUNCH_TYPE);
        if (type == null) {
            throw new IllegalStateException("JUnit launch configuration type not available (JDT JUnit plugin missing)");
        }
        return type.newInstance(null, manager.generateLaunchConfigurationName(name));
    }

    private static String launchAndCapture(ILaunchConfigurationWorkingCopy wc, long timeoutMillis) throws Exception {
        ILaunchConfiguration config = wc.doSave();
        ILaunch launch = config.launch(ILaunchManager.RUN_MODE, null);

        StringBuilder output = new StringBuilder();
        long attachDeadline = System.currentTimeMillis() + 5000;
        IProcess[] processes;
        while ((processes = launch.getProcesses()).length == 0 && System.currentTimeMillis() < attachDeadline) {
            Thread.sleep(50);
        }
        for (IProcess process : processes) {
            IStreamMonitor out = process.getStreamsProxy() != null ? process.getStreamsProxy().getOutputStreamMonitor() : null;
            IStreamMonitor err = process.getStreamsProxy() != null ? process.getStreamsProxy().getErrorStreamMonitor() : null;
            if (out != null) {
                out.addListener((text, monitor) -> output.append(text));
                output.append(out.getContents());
            }
            if (err != null) {
                err.addListener((text, monitor) -> output.append(text));
                output.append(err.getContents());
            }
        }

        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!launch.isTerminated()) {
            if (System.currentTimeMillis() > deadline) {
                launch.terminate();
                output.append("\n[timed out after ").append(timeoutMillis).append("ms, process terminated]\n");
                return output.toString();
            }
            Thread.sleep(200);
        }

        for (IProcess process : processes) {
            output.append("\n=== ").append(process.getLabel()).append(" (exit ").append(safeExitValue(process))
                    .append(") ===\n");
        }
        return output.toString();
    }

    private static String safeExitValue(IProcess process) {
        try {
            return String.valueOf(process.getExitValue());
        } catch (org.eclipse.debug.core.DebugException e) {
            return "unknown";
        }
    }
}
