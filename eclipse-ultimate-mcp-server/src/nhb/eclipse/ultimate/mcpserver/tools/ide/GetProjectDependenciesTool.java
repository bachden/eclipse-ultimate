package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists a Java project's classpath entries: project references, libraries and containers. */
public class GetProjectDependenciesTool implements McpTool {

    @Override
    public String name() {
        return "get_project_dependencies";
    }

    @Override
    public String description() {
        return "List a Java project's classpath entries: referenced projects, library jars and classpath "
                + "containers (e.g. Maven dependencies, JRE).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project to inspect");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        IJavaProject javaProject = Workspace.javaProject(projectName);

        JsonArray entries = new JsonArray();
        for (IClasspathEntry entry : javaProject.getRawClasspath()) {
            JsonObject e = new JsonObject();
            e.addProperty("kind", kindName(entry.getEntryKind()));
            e.addProperty("path", entry.getPath().toString());
            entries.add(e);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(entries);
    }

    private String kindName(int kind) {
        switch (kind) {
        case IClasspathEntry.CPE_SOURCE:
            return "SOURCE";
        case IClasspathEntry.CPE_PROJECT:
            return "PROJECT";
        case IClasspathEntry.CPE_LIBRARY:
            return "LIBRARY";
        case IClasspathEntry.CPE_VARIABLE:
            return "VARIABLE";
        case IClasspathEntry.CPE_CONTAINER:
            return "CONTAINER";
        default:
            return "UNKNOWN";
        }
    }
}
