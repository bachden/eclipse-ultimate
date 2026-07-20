package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

/** Shared helpers for resolving JDT types by fully-qualified name. */
final class JdtLookup {

    private JdtLookup() {
    }

    /** Resolve a fully-qualified type name (e.g. com.foo.Bar) to its {@link IType}. */
    static IType findType(String projectName, String fqName) throws JavaModelException {
        IJavaProject javaProject = Workspace.javaProject(projectName);
        IType type = javaProject.findType(fqName);
        if (type == null) {
            throw new IllegalArgumentException("Type not found: " + fqName + " in project " + projectName);
        }
        return type;
    }

    static ICompilationUnit compilationUnit(IType type) {
        ICompilationUnit unit = type.getCompilationUnit();
        if (unit == null) {
            throw new IllegalArgumentException("Type " + type.getFullyQualifiedName() + " has no source (binary/library type)");
        }
        return unit;
    }
}
