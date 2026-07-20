package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunchConfiguration;

/** Shared lookup of a saved launch configuration by its exact display name. */
final class LaunchConfigurationLookup {

    private LaunchConfigurationLookup() {
    }

    static ILaunchConfiguration find(String name) throws Exception {
        for (ILaunchConfiguration candidate : DebugHandles.manager().getLaunchConfigurations()) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No launch configuration named: " + name);
    }
}
