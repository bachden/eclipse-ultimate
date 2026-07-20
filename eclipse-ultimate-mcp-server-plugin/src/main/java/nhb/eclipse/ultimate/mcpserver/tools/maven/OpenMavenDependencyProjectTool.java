package nhb.eclipse.ultimate.mcpserver.tools.maven;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Opens the workspace project selected by m2e for a dependency. */
public class OpenMavenDependencyProjectTool implements McpTool {

    @Override
    public String name() {
        return "open_maven_dependency_project";
    }

    @Override
    public String description() {
        return "Open the existing workspace project that m2e resolves for a dependency. Does not import projects "
                + "or extract JARs; fails when the dependency has no workspace project match.";
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
        if (artifact == null) {
            throw new IllegalArgumentException(
                    "Dependency is not resolved in project " + projectName + ": " + groupId + ":" + artifactId);
        }

        IMavenProjectFacade dependencyFacade = MavenSupport.workspaceFacade(artifact);
        if (dependencyFacade == null) {
            throw new IllegalStateException("m2e resolves this dependency to an artifact, not a workspace project: "
                    + MavenSupport.artifactId(artifact));
        }

        IProject dependencyProject = dependencyFacade.getProject();
        boolean wasOpen = dependencyProject.isOpen();
        if (!wasOpen) {
            dependencyProject.open(new NullProgressMonitor());
        }

        JsonObject result = new JsonObject();
        result.addProperty("dependency", MavenSupport.artifactId(artifact));
        result.addProperty("workspaceProject", dependencyProject.getName());
        result.addProperty("wasOpen", wasOpen);
        result.addProperty("isOpen", dependencyProject.isOpen());
        if (dependencyProject.getLocation() != null) {
            result.addProperty("workspaceProjectLocation", dependencyProject.getLocation().toOSString());
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
