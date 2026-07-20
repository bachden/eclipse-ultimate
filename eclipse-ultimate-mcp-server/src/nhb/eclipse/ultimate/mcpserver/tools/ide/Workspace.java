package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/** Small shared helpers for locating projects/resources by name. */
final class Workspace {

    private Workspace() {
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

    static IJavaProject javaProject(String name) {
        IProject project = project(name);
        IJavaProject javaProject = JavaCore.create(project);
        if (javaProject == null || !javaProject.exists()) {
            throw new IllegalArgumentException("Not a Java project: " + name);
        }
        return javaProject;
    }

    /** Resolve a project-relative path (may be empty/null for the project root). */
    static IResource resource(IProject project, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return project;
        }
        IPath path = new Path(relativePath);
        IResource resource = project.findMember(path);
        if (resource == null) {
            throw new IllegalArgumentException("No such resource: " + relativePath + " in project " + project.getName());
        }
        return resource;
    }

    static IWorkspace workspace() {
        return ResourcesPlugin.getWorkspace();
    }
}
