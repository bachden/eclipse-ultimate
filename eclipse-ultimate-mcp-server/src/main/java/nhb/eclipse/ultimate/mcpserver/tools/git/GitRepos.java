package nhb.eclipse.ultimate.mcpserver.tools.git;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

/** Resolves the JGit {@link Repository} backing an Eclipse project, via EGit's repository mapping. */
final class GitRepos {

    private GitRepos() {
    }

    static IProject project(String name) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(name);
        if (!project.exists()) {
            throw new IllegalArgumentException("No such project: " + name);
        }
        return project;
    }

    static Repository repository(String projectName) {
        IProject project = project(projectName);
        RepositoryMapping mapping = RepositoryMapping.getMapping(project);
        if (mapping == null || mapping.getRepository() == null) {
            throw new IllegalArgumentException("Project " + projectName + " is not under Git version control");
        }
        return mapping.getRepository();
    }

    static Git git(String projectName) {
        return new Git(repository(projectName));
    }
}
