package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists Java problem markers (errors/warnings) for a project, or a single file within it. */
public class GetCompilationErrorsTool implements McpTool {

    @Override
    public String name() {
        return "get_compilation_errors";
    }

    @Override
    public String description() {
        return "Get Java compilation problem markers (errors and warnings) for a project, optionally filtered to "
                + "a single file (relative path).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project to check");
        Schemas.prop(schema, "filePath", "string", "Optional path relative to the project root to filter to a single file");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.optString(arguments, "filePath", null);

        IProject project = Workspace.project(projectName);
        IResource scope = filePath != null ? Workspace.resource(project, filePath) : project;

        IMarker[] markers = scope.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
        JsonArray results = new JsonArray();
        for (IMarker marker : markers) {
            int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
            JsonObject entry = new JsonObject();
            entry.addProperty("path", marker.getResource().getProjectRelativePath().toString());
            entry.addProperty("line", marker.getAttribute(IMarker.LINE_NUMBER, -1));
            entry.addProperty("severity", severity == IMarker.SEVERITY_ERROR ? "ERROR"
                    : severity == IMarker.SEVERITY_WARNING ? "WARNING" : "INFO");
            entry.addProperty("message", String.valueOf(marker.getAttribute(IMarker.MESSAGE, "")));
            results.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
