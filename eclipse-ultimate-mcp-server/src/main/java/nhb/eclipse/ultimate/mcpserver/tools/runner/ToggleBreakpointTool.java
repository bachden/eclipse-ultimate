package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.JDIDebugModel;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Sets (or removes) a Java line breakpoint at a given type + line. */
public class ToggleBreakpointTool implements McpTool {

    @Override
    public String name() {
        return "toggle_breakpoint";
    }

    @Override
    public String description() {
        return "Set or remove a Java line breakpoint, given the project, fully-qualified type name and line "
                + "number. If a breakpoint already exists at that exact location it is removed instead of "
                + "duplicated.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the type belongs to");
        Schemas.prop(schema, "fqName", "string", "Fully-qualified type name, e.g. com.example.Foo");
        Schemas.prop(schema, "line", "integer", "1-based line number");
        return Schemas.required(schema, "projectName", "fqName", "line");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqName = Schemas.requireString(arguments, "fqName");
        int line = Schemas.optInt(arguments, "line", -1);

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (!project.exists()) {
            throw new IllegalArgumentException("No such project: " + projectName);
        }
        IType type = JavaCore.create(project).findType(fqName);
        if (type == null) {
            throw new IllegalArgumentException("Type not found: " + fqName);
        }
        IResource resource = type.getUnderlyingResource();

        org.eclipse.debug.core.model.IBreakpoint existing = null;
        for (org.eclipse.debug.core.model.IBreakpoint bp : org.eclipse.debug.core.DebugPlugin.getDefault()
                .getBreakpointManager().getBreakpoints()) {
            if (bp instanceof org.eclipse.jdt.debug.core.IJavaLineBreakpoint) {
                org.eclipse.jdt.debug.core.IJavaLineBreakpoint lineBp = (org.eclipse.jdt.debug.core.IJavaLineBreakpoint) bp;
                if (fqName.equals(lineBp.getTypeName()) && lineBp.getLineNumber() == line) {
                    existing = bp;
                    break;
                }
            }
        }

        if (existing != null) {
            existing.delete();
            return "Removed breakpoint at " + fqName + ":" + line;
        }

        JDIDebugModel.createLineBreakpoint(resource, fqName, line, -1, -1, 0, true, null);
        return "Set breakpoint at " + fqName + ":" + line;
    }
}
