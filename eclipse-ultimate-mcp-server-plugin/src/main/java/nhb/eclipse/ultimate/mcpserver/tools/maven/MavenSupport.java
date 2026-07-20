package nhb.eclipse.ultimate.mcpserver.tools.maven;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;

import com.google.gson.JsonObject;

/**
 * Shared m2e access. Maven runtime classes are intentionally not referenced at
 * compile time because m2e embeds them in a non-exported runtime bundle.
 */
final class MavenSupport {

    private static final Set<String> SCOPES = Set.of("compile", "provided", "runtime", "test", "system", "import");

    private MavenSupport() {
    }

    static IMavenProjectFacade facade(String projectName) throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists()) {
            throw new IllegalArgumentException("Project does not exist: " + projectName);
        }
        if (!project.isOpen()) {
            throw new IllegalStateException("Project is closed: " + projectName);
        }
        if (!MavenPlugin.isMavenProject(project)) {
            throw new IllegalArgumentException("Project is not managed by m2e: " + projectName);
        }

        IMavenProjectRegistry registry = MavenPlugin.getMavenProjectRegistry();
        IMavenProjectFacade facade = registry.getProject(project);
        if (facade == null) {
            facade = registry.create(project, new NullProgressMonitor());
        }
        if (facade == null) {
            throw new IllegalStateException("m2e has no Maven project facade for: " + projectName);
        }
        return facade;
    }

    static MavenProjectModel mavenProject(IMavenProjectFacade facade) throws Exception {
        IProgressMonitor monitor = new NullProgressMonitor();
        Method getMavenProject = IMavenProjectFacade.class.getMethod("getMavenProject", IProgressMonitor.class);
        Object rawProject = invokeMethod(getMavenProject, facade, monitor);
        if (rawProject == null) {
            throw new IllegalStateException(
                    "m2e could not build the Maven model for: " + facade.getProject().getName());
        }

        String groupId = stringValue(rawProject, "getGroupId");
        String artifactId = stringValue(rawProject, "getArtifactId");
        String version = stringValue(rawProject, "getVersion");
        String packaging = stringValue(rawProject, "getPackaging");
        if (packaging == null || packaging.isBlank()) {
            packaging = "jar";
        }

        List<MavenArtifactModel> artifacts = new ArrayList<>();
        Object rawArtifacts = invoke(rawProject, "getArtifacts");
        if (rawArtifacts instanceof Iterable<?> iterable) {
            for (Object rawArtifact : iterable) {
                artifacts.add(artifact(rawArtifact));
            }
        }
        artifacts.sort(Comparator.comparing(MavenSupport::artifactId));
        return new MavenProjectModel(groupId, artifactId, packaging, version, artifacts);
    }

    private static MavenArtifactModel artifact(Object rawArtifact) throws Exception {
        List<String> trail = new ArrayList<>();
        Object rawTrail = invoke(rawArtifact, "getDependencyTrail");
        if (rawTrail instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    trail.add(item.toString());
                }
            }
        }

        Object rawFile = invoke(rawArtifact, "getFile");
        File file = rawFile instanceof File ? (File) rawFile : null;
        return new MavenArtifactModel(stringValue(rawArtifact, "getGroupId"), stringValue(rawArtifact, "getArtifactId"),
                stringValue(rawArtifact, "getType"), stringValue(rawArtifact, "getClassifier"),
                stringValue(rawArtifact, "getVersion"), stringValue(rawArtifact, "getBaseVersion"),
                stringValue(rawArtifact, "getScope"), booleanValue(rawArtifact, "isOptional"),
                booleanValue(rawArtifact, "isResolved"), file, trail);
    }

    private static String stringValue(Object target, String methodName) throws Exception {
        Object value = invoke(target, methodName);
        return value == null ? "" : value.toString();
    }

    private static boolean booleanValue(Object target, String methodName) throws Exception {
        Object value = invoke(target, methodName);
        return value instanceof Boolean ? ((Boolean) value).booleanValue()
                : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object invokeMethod(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static Object invoke(Object target, String methodName, Object... arguments) throws Exception {
        Method method;
        if (arguments.length == 0) {
            method = target.getClass().getMethod(methodName);
        } else {
            method = findMethod(target.getClass(), methodName, arguments);
        }
        return invokeMethod(method, target, arguments);
    }

    private static Method findMethod(Class<?> type, String name, Object[] arguments) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < arguments.length; i++) {
                if (!method.getParameterTypes()[i].isInstance(arguments[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    static List<MavenArtifactModel> sortedArtifacts(MavenProjectModel project) {
        return project.artifacts();
    }

    static MavenArtifactModel findArtifact(MavenProjectModel project, String groupId, String artifactId, String version,
            String type, String classifier) {
        List<MavenArtifactModel> matches = project.artifacts().stream()
                .filter(artifact -> groupId.equals(artifact.groupId()))
                .filter(artifact -> artifactId.equals(artifact.artifactId()))
                .filter(artifact -> version == null || version.isBlank() || version.equals(artifact.version())
                        || version.equals(artifact.baseVersion()))
                .filter(artifact -> type == null || type.isBlank() || type.equals(artifact.type()))
                .filter(artifact -> classifier == null || classifier.equals(nullToEmpty(artifact.classifier())))
                .toList();

        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return matches.stream()
                .filter(artifact -> "jar".equals(artifact.type()) && nullToEmpty(artifact.classifier()).isEmpty())
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Dependency selector is ambiguous; specify version, type or classifier"));
    }

    static IMavenProjectFacade workspaceFacade(MavenArtifactModel artifact) {
        return workspaceFacade(artifact.groupId(), artifact.artifactId(), artifact.version());
    }

    static IMavenProjectFacade workspaceFacade(String groupId, String artifactId, String version) {
        IMavenProjectRegistry registry = MavenPlugin.getMavenProjectRegistry();
        IMavenProjectFacade exact = registry.getMavenProject(groupId, artifactId, version);
        if (exact != null) {
            return exact;
        }
        for (IMavenProjectFacade candidate : registry.getProjects()) {
            ArtifactKey key = candidate.getArtifactKey();
            if (key != null && groupId.equals(key.groupId()) && artifactId.equals(key.artifactId())
                    && version.equals(key.version())) {
                return candidate;
            }
        }
        return null;
    }

    static JsonObject artifactJson(MavenArtifactModel artifact) {
        JsonObject result = coordinateJson(artifact.groupId(), artifact.artifactId(), artifact.type(),
                artifact.classifier(), artifact.version());
        result.addProperty("scope", artifact.scope());
        result.addProperty("optional", artifact.optional());
        result.addProperty("resolved", artifact.resolved());

        if (artifact.file() != null) {
            result.addProperty("artifactFile", artifact.file().getAbsolutePath());
        }

        IMavenProjectFacade workspaceFacade = workspaceFacade(artifact);
        if (workspaceFacade != null) {
            IProject workspaceProject = workspaceFacade.getProject();
            result.addProperty("workspaceProject", workspaceProject.getName());
            result.addProperty("workspaceProjectOpen", workspaceProject.isOpen());
            if (workspaceProject.getLocation() != null) {
                result.addProperty("workspaceProjectLocation", workspaceProject.getLocation().toOSString());
            }
            result.addProperty("preferredCodeSource", "workspaceProject");
        } else if (artifact.file() != null) {
            result.addProperty("preferredCodeSource", "artifactFile");
        }
        return result;
    }

    static JsonObject coordinateJson(String groupId, String artifactId, String type, String classifier,
            String version) {
        JsonObject result = new JsonObject();
        result.addProperty("id", coordinateId(groupId, artifactId, type, classifier, version));
        result.addProperty("groupId", groupId);
        result.addProperty("artifactId", artifactId);
        result.addProperty("type", type);
        if (classifier != null && !classifier.isBlank()) {
            result.addProperty("classifier", classifier);
        }
        result.addProperty("version", version);
        return result;
    }

    static String artifactId(MavenArtifactModel artifact) {
        return coordinateId(artifact.groupId(), artifact.artifactId(), artifact.type(), artifact.classifier(),
                artifact.version());
    }

    static String projectId(MavenProjectModel project) {
        return coordinateId(project.groupId(), project.artifactId(), project.packaging(), null, project.version());
    }

    static ParsedCoordinate parseTrailCoordinate(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length < 4) {
            return new ParsedCoordinate(value, null, null, null, null, null);
        }

        int end = parts.length;
        if (SCOPES.contains(parts[end - 1])) {
            end--;
        }
        if (end < 4) {
            return new ParsedCoordinate(value, null, null, null, null, null);
        }

        String classifier = end > 4 ? parts[3] : null;
        String version = parts[end - 1];
        String id = coordinateId(parts[0], parts[1], parts[2], classifier, version);
        return new ParsedCoordinate(id, parts[0], parts[1], parts[2], classifier, version);
    }

    static String coordinateId(String groupId, String artifactId, String type, String classifier, String version) {
        StringBuilder id = new StringBuilder();
        id.append(groupId).append(':').append(artifactId).append(':').append(type);
        if (classifier != null && !classifier.isBlank()) {
            id.append(':').append(classifier);
        }
        return id.append(':').append(version).toString();
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record MavenProjectModel(String groupId, String artifactId, String packaging, String version,
            List<MavenArtifactModel> artifacts) {
    }

    record MavenArtifactModel(String groupId, String artifactId, String type, String classifier, String version,
            String baseVersion, String scope, boolean optional, boolean resolved, File file,
            List<String> dependencyTrail) {
    }

    record ParsedCoordinate(String id, String groupId, String artifactId, String type, String classifier,
            String version) {
        JsonObject toJson() {
            if (groupId == null) {
                JsonObject result = new JsonObject();
                result.addProperty("id", id);
                return result;
            }
            JsonObject result = coordinateJson(groupId, artifactId, type, classifier, version);
            IMavenProjectFacade facade = workspaceFacade(groupId, artifactId, version);
            if (facade != null) {
                result.addProperty("workspaceProject", facade.getProject().getName());
                result.addProperty("workspaceProjectOpen", facade.getProject().isOpen());
            }
            return result;
        }
    }
}
