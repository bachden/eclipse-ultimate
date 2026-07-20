package nhb.eclipse.ultimate.mcpserver.tools.maven;

import org.eclipse.m2e.core.ui.internal.editing.PomEdits;
import org.w3c.dom.Element;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Adds or updates one direct Maven dependency through m2e's POM editor. */
public class ConfigureMavenDependencyTool implements McpTool {

    @Override
    public String name() {
        return "configure_maven_dependency";
    }

    @Override
    public String description() {
        return "Add or update a direct Maven dependency using m2e's DOM POM editor, preserving surrounding XML "
                + "formatting and comments. Supports dependencyManagement, scope, type, classifier, optional and exclusions.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The open m2e project whose pom.xml to edit");
        Schemas.prop(schema, "groupId", "string", "Dependency groupId");
        Schemas.prop(schema, "artifactId", "string", "Dependency artifactId");
        Schemas.prop(schema, "version", "string",
                "Optional version; omit to preserve, or use an empty string to remove for managed versions");
        Schemas.prop(schema, "type", "string", "Optional dependency type; defaults to jar");
        Schemas.prop(schema, "classifier", "string", "Optional dependency classifier");
        Schemas.prop(schema, "scope", "string", "Optional dependency scope");
        Schemas.prop(schema, "optional", "boolean", "Optional dependency flag; false removes the optional element");
        Schemas.prop(schema, "dependencyManagement", "boolean",
                "Edit dependencyManagement instead of direct dependencies (default false)");
        addExclusions(schema);
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
        String type = optionalString(arguments, "type");
        String classifier = optionalString(arguments, "classifier");
        boolean dependencyManagement = Schemas.optBoolean(arguments, "dependencyManagement", false);
        boolean refreshProject = Schemas.optBoolean(arguments, "refreshProject", true);
        boolean forceDependencyUpdate = Schemas.optBoolean(arguments, "forceDependencyUpdate", false);
        boolean[] added = new boolean[1];

        MavenDependencyEditSupport.apply(projectName, document -> {
            Element dependencies = MavenDependencyEditSupport.dependencies(document, dependencyManagement, true);
            Element dependency = MavenDependencyEditSupport.findDependency(dependencies, groupId, artifactId, type,
                    classifier);
            if (dependency == null) {
                dependency = MavenDependencyEditSupport.createDependency(dependencies, groupId, artifactId);
                added[0] = true;
            }

            MavenDependencyEditSupport.setField(dependency, PomEdits.VERSION, optionalString(arguments, "version"));
            MavenDependencyEditSupport.setField(dependency, PomEdits.TYPE, type);
            MavenDependencyEditSupport.setField(dependency, PomEdits.CLASSIFIER, classifier);
            MavenDependencyEditSupport.setField(dependency, PomEdits.SCOPE, optionalString(arguments, "scope"));
            if (arguments.has("optional") && !arguments.get("optional").isJsonNull()) {
                MavenDependencyEditSupport.setField(dependency, PomEdits.OPTIONAL,
                        arguments.get("optional").getAsBoolean() ? "true" : "");
            }
            replaceExclusions(dependency, arguments);
        }, "Configure Maven dependency " + groupId + ":" + artifactId, refreshProject, forceDependencyUpdate);

        JsonObject result = new JsonObject();
        result.addProperty("projectName", projectName);
        result.addProperty("action", added[0] ? "added" : "updated");
        result.addProperty("groupId", groupId);
        result.addProperty("artifactId", artifactId);
        result.addProperty("dependencyManagement", dependencyManagement);
        result.addProperty("m2eRefreshed", refreshProject);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private static void replaceExclusions(Element dependency, JsonObject arguments) {
        if (!arguments.has("exclusions") || arguments.get("exclusions").isJsonNull()) {
            return;
        }
        if (!arguments.get("exclusions").isJsonArray()) {
            throw new IllegalArgumentException("exclusions must be an array");
        }

        Element existing = PomEdits.findChild(dependency, PomEdits.EXCLUSIONS);
        if (existing != null) {
            PomEdits.removeChild(dependency, existing);
        }

        JsonArray exclusions = arguments.getAsJsonArray("exclusions");
        if (exclusions.isEmpty()) {
            return;
        }
        Element parent = PomEdits.getChild(dependency, new String[] { PomEdits.EXCLUSIONS });
        for (JsonElement item : exclusions) {
            if (!item.isJsonObject()) {
                throw new IllegalArgumentException("Each exclusion must be an object");
            }
            JsonObject exclusion = item.getAsJsonObject();
            Element element = PomEdits.createElement(parent, PomEdits.EXCLUSION);
            PomEdits.createElementWithText(element, PomEdits.GROUP_ID, Schemas.requireString(exclusion, "groupId"));
            PomEdits.createElementWithText(element, PomEdits.ARTIFACT_ID,
                    Schemas.requireString(exclusion, "artifactId"));
        }
    }

    private static String optionalString(JsonObject arguments, String name) {
        return arguments.has(name) && !arguments.get(name).isJsonNull() ? arguments.get(name).getAsString().trim()
                : null;
    }

    private static void addExclusions(JsonObject schema) {
        JsonObject exclusion = new JsonObject();
        exclusion.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        properties.add("groupId", scalar("Dependency groupId to exclude"));
        properties.add("artifactId", scalar("Dependency artifactId to exclude"));
        exclusion.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("groupId");
        required.add("artifactId");
        exclusion.add("required", required);

        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        array.addProperty("description", "Optional final exclusion list; an empty array removes all exclusions");
        array.add("items", exclusion);
        schema.getAsJsonObject("properties").add("exclusions", array);
    }

    private static JsonObject scalar(String description) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("description", description);
        return value;
    }
}
