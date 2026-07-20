package nhb.eclipse.ultimate.mcpserver.tools.maven;

import org.eclipse.core.resources.IProject;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Refreshes one Maven project using the same m2e project configuration manager
 * as Eclipse.
 */
public class RefreshMavenProjectTool implements McpTool {

    @Override
    public String name() {
        return "refresh_maven_project";
    }

    @Override
    public String description() {
        return "Refresh a Maven project's effective model, classpath and m2e configuration after pom.xml changes.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The open m2e project to refresh");
        Schemas.prop(schema, "forceDependencyUpdate", "boolean",
                "Force dependency and snapshot updates during refresh (default false)");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        boolean forceDependencyUpdate = Schemas.optBoolean(arguments, "forceDependencyUpdate", false);
        IProject project = MavenSupport.facade(projectName).getProject();
        MavenDependencyEditSupport.refresh(project, forceDependencyUpdate);

        JsonObject result = new JsonObject();
        result.addProperty("projectName", projectName);
        result.addProperty("refreshed", true);
        result.addProperty("forceDependencyUpdate", forceDependencyUpdate);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
