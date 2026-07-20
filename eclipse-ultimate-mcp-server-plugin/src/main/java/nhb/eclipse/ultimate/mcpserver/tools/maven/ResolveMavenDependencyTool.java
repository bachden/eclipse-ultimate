package nhb.eclipse.ultimate.mcpserver.tools.maven;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Resolves a dependency through m2e and reports any matching workspace project.
 */
public class ResolveMavenDependencyTool implements McpTool {

    @Override
    public String name() {
        return "resolve_maven_dependency";
    }

    @Override
    public String description() {
        return "Resolve a Maven dependency using m2e's current project model. Reports the selected artifact and "
                + "the workspace project that substitutes for it, so source lookup can avoid JAR extraction.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The open m2e project whose resolved dependencies to search");
        Schemas.prop(schema, "groupId", "string", "Dependency groupId");
        Schemas.prop(schema, "artifactId", "string", "Dependency artifactId");
        Schemas.prop(schema, "version", "string", "Optional selected version; omit to use m2e's resolved version");
        Schemas.prop(schema, "type", "string", "Optional artifact type, such as jar or test-jar");
        Schemas.prop(schema, "classifier", "string", "Optional classifier; omit to select the main artifact");
        return Schemas.required(schema, "projectName", "groupId", "artifactId");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String groupId = Schemas.requireString(arguments, "groupId");
        String artifactId = Schemas.requireString(arguments, "artifactId");
        String version = Schemas.optString(arguments, "version", "");
        String type = Schemas.optString(arguments, "type", "");
        String classifier = Schemas.optString(arguments, "classifier", "");

        MavenSupport.MavenProjectModel project = MavenSupport.mavenProject(MavenSupport.facade(projectName));
        MavenSupport.MavenArtifactModel artifact = MavenSupport.findArtifact(project, groupId, artifactId, version,
                type, classifier);

        JsonObject result = new JsonObject();
        result.addProperty("projectName", projectName);
        if (artifact == null) {
            result.addProperty("found", false);
            result.add("availableMatches", availableMatches(project, groupId, artifactId));
        } else {
            result.addProperty("found", true);
            result.add("dependency", MavenSupport.artifactJson(artifact));
            result.addProperty("workspaceProjectResolved", MavenSupport.workspaceFacade(artifact) != null);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private static JsonArray availableMatches(MavenSupport.MavenProjectModel project, String groupId,
            String artifactId) {
        JsonArray matches = new JsonArray();
        MavenSupport.sortedArtifacts(project).stream()
                .filter(artifact -> groupId.equals(artifact.groupId()) && artifactId.equals(artifact.artifactId()))
                .map(MavenSupport::artifactJson).forEach(matches::add);
        return matches;
    }
}
