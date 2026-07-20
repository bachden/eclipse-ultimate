package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Prints a project's (or a subtree's) file/folder structure as an indented tree. */
public class GetProjectLayoutTool implements McpTool {

    @Override
    public String name() {
        return "get_project_layout";
    }

    @Override
    public String description() {
        return "Get the file and folder structure of a project in a hierarchical format. Use scopePath to limit "
                + "to a subdirectory and/or maxDepth to limit tree depth.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The name of the project to analyze");
        Schemas.prop(schema, "scopePath", "string", "Optional path relative to the project root to limit the listing");
        Schemas.prop(schema, "maxDepth", "integer", "Optional maximum depth of the directory tree to display");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String scopePath = Schemas.optString(arguments, "scopePath", null);
        int maxDepth = Schemas.optInt(arguments, "maxDepth", Integer.MAX_VALUE);

        IProject project = Workspace.project(projectName);
        IResource root = Workspace.resource(project, scopePath);

        StringBuilder sb = new StringBuilder();
        sb.append("# Project Structure: ").append(projectName);
        if (scopePath != null && !scopePath.isEmpty()) {
            sb.append("/").append(scopePath);
        }
        sb.append("\n\n");
        sb.append("- ").append(root.getName()).append(" (").append(kind(root)).append(")\n");
        if (root instanceof IContainer) {
            walk((IContainer) root, 1, maxDepth, sb);
        }
        return sb.toString();
    }

    private void walk(IContainer container, int depth, int maxDepth, StringBuilder sb) throws Exception {
        if (depth > maxDepth) {
            return;
        }
        IResource[] members = container.members();
        for (IResource member : members) {
            sb.append("  ".repeat(depth)).append("- ").append(member.getName()).append(" (").append(kind(member))
                    .append(")\n");
            if (member instanceof IContainer && depth < maxDepth) {
                walk((IContainer) member, depth + 1, maxDepth, sb);
            }
        }
    }

    private String kind(IResource resource) {
        switch (resource.getType()) {
        case IResource.PROJECT:
            return "Project";
        case IResource.FOLDER:
            return "Directory";
        case IResource.FILE:
            return "File";
        default:
            return "Resource";
        }
    }
}
