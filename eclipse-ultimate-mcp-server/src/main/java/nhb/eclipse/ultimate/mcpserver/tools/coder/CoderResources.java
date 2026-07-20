package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/** Shared helpers for locating projects/resources by name (coder tool package). */
final class CoderResources {

    private CoderResources() {
    }

    static IWorkspaceRoot root() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    static IProject project(String name) {
        IProject project = root().getProject(name);
        if (!project.exists()) {
            throw new IllegalArgumentException("No such project: " + name);
        }
        return project;
    }

    static IResource resource(IProject project, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return project;
        }
        IPath path = new Path(relativePath);
        IResource resource = project.findMember(path);
        if (resource == null) {
            throw new IllegalArgumentException(
                    "No such resource: " + relativePath + " in project " + project.getName());
        }
        return resource;
    }

    static IPath fullPath(String projectName, String relativePath) {
        return project(projectName).getFullPath().append(relativePath);
    }
}
