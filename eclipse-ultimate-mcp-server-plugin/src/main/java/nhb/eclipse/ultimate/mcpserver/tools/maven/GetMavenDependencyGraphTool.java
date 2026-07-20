package nhb.eclipse.ultimate.mcpserver.tools.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns the resolved dependency graph from m2e's current Maven model. */
public class GetMavenDependencyGraphTool implements McpTool {

    @Override
    public String name() {
        return "get_maven_dependency_graph";
    }

    @Override
    public String description() {
        return "Get the resolved, dependency-mediated Maven graph from m2e. Includes dependency trails, "
                + "resolved artifact paths, and matching workspace projects when available.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The open m2e Maven project to inspect");
        Schemas.prop(schema, "scope", "string",
                "Optional resolved scope filter: compile, provided, runtime, test, system or import");
        Schemas.prop(schema, "includeOptional", "boolean", "Include optional dependencies (default true)");
        Schemas.prop(schema, "maxDepth", "integer", "Maximum dependency depth to return (default 100)");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String scope = Schemas.optString(arguments, "scope", "").trim();
        boolean includeOptional = Schemas.optBoolean(arguments, "includeOptional", true);
        int maxDepth = Math.max(1, Math.min(Schemas.optInt(arguments, "maxDepth", 100), 100));

        var facade = MavenSupport.facade(projectName);
        MavenSupport.MavenProjectModel project = MavenSupport.mavenProject(facade);
        String rootId = MavenSupport.projectId(project);

        Map<String, JsonObject> nodes = new LinkedHashMap<>();
        nodes.put(rootId, rootNode(project, facade));
        Set<String> edgeKeys = new LinkedHashSet<>();
        JsonArray dependencies = new JsonArray();
        int omittedByFilter = 0;

        for (MavenSupport.MavenArtifactModel artifact : MavenSupport.sortedArtifacts(project)) {
            if (!scope.isEmpty() && !scope.equals(artifact.scope()) || !includeOptional && artifact.optional()) {
                omittedByFilter++;
                continue;
            }

            List<String> trail = normalizedTrail(rootId, artifact);
            int depth = Math.max(1, trail.size() - 1);
            if (depth > maxDepth) {
                omittedByFilter++;
                continue;
            }

            for (String id : trail) {
                nodes.computeIfAbsent(id, ignored -> MavenSupport.parseTrailCoordinate(id).toJson());
            }
            nodes.put(MavenSupport.artifactId(artifact), MavenSupport.artifactJson(artifact));

            for (int index = 1; index < trail.size(); index++) {
                edgeKeys.add(trail.get(index - 1) + "\n" + trail.get(index));
            }

            JsonObject dependency = MavenSupport.artifactJson(artifact);
            dependency.addProperty("depth", depth);
            JsonArray trailJson = new JsonArray();
            trail.forEach(trailJson::add);
            dependency.add("dependencyTrail", trailJson);
            dependencies.add(dependency);
        }

        JsonObject result = new JsonObject();
        result.addProperty("projectName", projectName);
        result.addProperty("graphType", "resolved-mediated");
        result.addProperty("rootId", rootId);
        result.addProperty("resolvedDependencyCount", dependencies.size());
        result.addProperty("omittedByFilter", omittedByFilter);

        JsonArray nodeArray = new JsonArray();
        nodes.values().forEach(nodeArray::add);
        result.add("nodes", nodeArray);

        JsonArray edges = new JsonArray();
        for (String edgeKey : edgeKeys) {
            int separator = edgeKey.indexOf('\n');
            JsonObject edge = new JsonObject();
            edge.addProperty("from", edgeKey.substring(0, separator));
            edge.addProperty("to", edgeKey.substring(separator + 1));
            edges.add(edge);
        }
        result.add("edges", edges);
        result.add("dependencies", dependencies);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private static JsonObject rootNode(MavenSupport.MavenProjectModel project,
            org.eclipse.m2e.core.project.IMavenProjectFacade facade) {
        JsonObject root = MavenSupport.coordinateJson(project.groupId(), project.artifactId(), project.packaging(),
                null, project.version());
        root.addProperty("workspaceProject", facade.getProject().getName());
        root.addProperty("workspaceProjectOpen", facade.getProject().isOpen());
        root.addProperty("root", true);
        return root;
    }

    private static List<String> normalizedTrail(String rootId, MavenSupport.MavenArtifactModel artifact) {
        List<String> result = new ArrayList<>();
        List<String> rawTrail = artifact.dependencyTrail();
        if (rawTrail != null) {
            rawTrail.stream().map(MavenSupport::parseTrailCoordinate).map(MavenSupport.ParsedCoordinate::id)
                    .forEach(result::add);
        }
        if (result.isEmpty() || !rootId.equals(result.get(0))) {
            result.add(0, rootId);
        }

        String artifactId = MavenSupport.artifactId(artifact);
        if (!artifactId.equals(result.get(result.size() - 1))) {
            result.add(artifactId);
        }
        return result;
    }
}
