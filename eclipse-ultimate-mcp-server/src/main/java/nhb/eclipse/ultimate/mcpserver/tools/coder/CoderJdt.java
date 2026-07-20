package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/** Shared JDT lookup helpers for the coder tool package. */
final class CoderJdt {

    private CoderJdt() {
    }

    static IJavaProject javaProject(String projectName) {
        IProject project = CoderResources.project(projectName);
        IJavaProject javaProject = JavaCore.create(project);
        if (javaProject == null || !javaProject.exists()) {
            throw new IllegalArgumentException("Not a Java project: " + projectName);
        }
        return javaProject;
    }

    static IType findType(String projectName, String fqName) throws JavaModelException {
        IType type = javaProject(projectName).findType(fqName);
        if (type == null) {
            throw new IllegalArgumentException("Type not found: " + fqName + " in project " + projectName);
        }
        return type;
    }

    static IPackageFragment findPackage(String projectName, String packageName) throws JavaModelException {
        for (org.eclipse.jdt.core.IPackageFragmentRoot root : javaProject(projectName).getPackageFragmentRoots()) {
            if (root.getKind() != org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE) {
                continue;
            }
            IPackageFragment fragment = root.getPackageFragment(packageName);
            if (fragment.exists()) {
                return fragment;
            }
        }
        throw new IllegalArgumentException("Package not found: " + packageName + " in project " + projectName);
    }
}
