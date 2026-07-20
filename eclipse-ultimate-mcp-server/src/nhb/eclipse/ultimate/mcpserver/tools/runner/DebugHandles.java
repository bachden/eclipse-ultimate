package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;

/**
 * Resolves launches/threads/frames by a stable-for-the-session index, since the Debug platform's
 * model objects don't carry a client-friendly id and each MCP tool call is a fresh HTTP request
 * with no object references to hand back and forth.
 */
final class DebugHandles {

    private DebugHandles() {
    }

    static ILaunchManager manager() {
        return DebugPlugin.getDefault().getLaunchManager();
    }

    static ILaunch launch(int launchIndex) {
        ILaunch[] launches = manager().getLaunches();
        if (launchIndex < 0 || launchIndex >= launches.length) {
            throw new IllegalArgumentException(
                    "No launch at index " + launchIndex + " (there are " + launches.length + ")");
        }
        return launches[launchIndex];
    }

    static IThread thread(int launchIndex, int threadIndex) throws Exception {
        ILaunch launch = launch(launchIndex);
        IThread[] threads = launch.getDebugTarget() != null ? launch.getDebugTarget().getThreads()
                : new IThread[0];
        if (threadIndex < 0 || threadIndex >= threads.length) {
            throw new IllegalArgumentException(
                    "No thread at index " + threadIndex + " in launch " + launchIndex + " (there are " + threads.length + ")");
        }
        return threads[threadIndex];
    }

    static IStackFrame frame(int launchIndex, int threadIndex, int frameIndex) throws Exception {
        IThread thread = thread(launchIndex, threadIndex);
        IStackFrame[] frames = thread.getStackFrames();
        if (frameIndex < 0 || frameIndex >= frames.length) {
            throw new IllegalArgumentException(
                    "No frame at index " + frameIndex + " in thread " + threadIndex + " (there are " + frames.length + ")");
        }
        return frames[frameIndex];
    }
}
