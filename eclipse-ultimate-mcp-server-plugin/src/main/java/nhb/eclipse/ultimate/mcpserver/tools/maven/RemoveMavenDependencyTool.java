package nhb.eclipse.ultimate.mcpserver.tools.maven;

import org.eclipse.m2e.core.ui.internal.editing.PomEdits;
import org.w3c.dom.Element;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Removes one direct Maven dependency without rewriting the complete POM. */
public class RemoveMavenDependencyTool implements McpTool {

    @Override
    public String name() {
        return "remove_maven_dependency";
    }

    @Override
    public String description() {
        return "Remove a matching direct Maven dependency through m2e's DOM POM editor. Preserves comments and formatting outside the removed dependency.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The open m2e project whose pom.xml to edit");
        Schemas.prop(schema, "groupId", "string", "Dependency groupId");
        Schemas.prop(schema, "artifactId", "string", "Dependency artifactId");
        Schemas.prop(schema, "type", "string", "Optional dependency type; defaults to jar");
        Schemas.prop(schema, "classifier", "string", "Optional dependency classifier");
        Schemas.prop(schema, "dependencyManagement", "boolean",
                "Remove from dependencyManagement instead of direct dependencies (default false)");
        Schemas.prop(schema, "failIfMissing", "boolean", "Fail when no matching dependency exists (default true)");
        Schemas.prop(schema, "refreshProject", "boolean",
                "Refresh the m2e project configuration after saving (default true)");
        Schemas.prop(schema, "forceDependencyUpdate", "boolean",
                "Force dependency and snapshot updates during refresh (default false)");
        return Schemas.required(schema, "projectName", "groupId", "artifactId");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String groupId = Schemas.requireString(arguments, "groupId");
        String artifactId = Schemas.requireString(arguments, "artifactId");
        String type = Schemas.optString(arguments, "type", "");
        String classifier = Schemas.optString(arguments, "classifier", "");
        boolean dependencyManagement = Schemas.optBoolean(arguments, "dependencyManagement", false);
        boolean failIfMissing = Schemas.optBoolean(arguments, "failIfMissing", true);
        boolean refreshProject = Schemas.optBoolean(arguments, "refreshProject", true);
        boolean forceDependencyUpdate = Schemas.optBoolean(arguments, "forceDependencyUpdate", false);
        boolean[] removed = new boolean[1];

        MavenDependencyEditSupport.apply(projectName, document -> {
            Element dependencies = MavenDependencyEditSupport.dependencies(document, dependencyManagement, false);
            Element dependency = MavenDependencyEditSupport.findDependency(dependencies, groupId, artifactId, type,
                    classifier);
            if (dependency == null) {
                if (failIfMissing) {
                    throw new IllegalArgumentException(
                            "Dependency not found in pom.xml: " + groupId + ":" + artifactId);
                }
                return;
            }
            PomEdits.removeChild(dependencies, dependency);
            MavenDependencyEditSupport.removeEmptyParents(document, dependencyManagement, dependencies);
            removed[0] = true;
        }, "Remove Maven dependency " + groupId + ":" + artifactId, refreshProject, forceDependencyUpdate);

        JsonObject result = new JsonObject();
        result.addProperty("projectName", projectName);
        result.addProperty("removed", removed[0]);
        result.addProperty("groupId", groupId);
        result.addProperty("artifactId", artifactId);
        result.addProperty("dependencyManagement", dependencyManagement);
        result.addProperty("m2eRefreshed", refreshProject);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
