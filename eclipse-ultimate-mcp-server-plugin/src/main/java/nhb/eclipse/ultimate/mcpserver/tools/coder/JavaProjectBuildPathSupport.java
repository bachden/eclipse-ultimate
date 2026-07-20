package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

final class JavaProjectBuildPathSupport {

    private JavaProjectBuildPathSupport() {
    }

    static IJavaProject javaProject(String projectName) {
        return CoderJdt.javaProject(projectName);
    }

    static String relativePath(JsonObject arguments, String name) {
        String value = arguments.has(name) && !arguments.get(name).isJsonNull()
                ? arguments.get(name).getAsString().trim()
                : "";
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        IPath path = new Path(normalized);
        if (path.isAbsolute() || path.segmentCount() == 0) {
            throw new IllegalArgumentException(name + " must be a project-relative folder path: " + value);
        }
        for (int i = 0; i < path.segmentCount(); i++) {
            if ("..".equals(path.segment(i)) || ".".equals(path.segment(i))) {
                throw new IllegalArgumentException(name + " cannot contain . or .. segments: " + value);
            }
        }
        return path.toString();
    }

    static IPath workspacePath(IJavaProject project, String relativePath) {
        return project.getPath().append(relativePath);
    }

    static IResource requireFolder(IJavaProject project, String relativePath, boolean createIfMissing)
            throws Exception {
        IFolder folder = project.getProject().getFolder(new Path(relativePath));
        if (!folder.exists()) {
            if (!createIfMissing) {
                throw new IllegalArgumentException("Folder does not exist: " + relativePath);
            }
            createFolder(folder);
        }
        if (!folder.exists() || !folder.isAccessible()) {
            throw new IllegalArgumentException("Folder is not accessible: " + relativePath);
        }
        return folder;
    }

    private static void createFolder(IFolder folder) throws Exception {
        IContainer parent = folder.getParent();
        if (parent instanceof IFolder parentFolder && !parentFolder.exists()) {
            createFolder(parentFolder);
        }
        folder.create(true, true, new NullProgressMonitor());
    }

    static IClasspathEntry sourceEntry(IClasspathEntry[] entries, IPath path) {
        for (IClasspathEntry entry : entries) {
            if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && path.equals(entry.getPath())) {
                return entry;
            }
        }
        return null;
    }

    static boolean isTestSource(IClasspathEntry entry) {
        if (entry == null) {
            return false;
        }
        for (IClasspathAttribute attribute : entry.getExtraAttributes()) {
            if (IClasspathAttribute.TEST.equals(attribute.getName())) {
                return Boolean.parseBoolean(attribute.getValue());
            }
        }
        return false;
    }

    static IClasspathAttribute[] attributes(IClasspathEntry existing, boolean testSource) {
        Map<String, String> values = new LinkedHashMap<>();
        if (existing != null) {
            for (IClasspathAttribute attribute : existing.getExtraAttributes()) {
                values.put(attribute.getName(), attribute.getValue());
            }
        }
        if (testSource) {
            values.put(IClasspathAttribute.TEST, Boolean.TRUE.toString());
        } else {
            values.remove(IClasspathAttribute.TEST);
        }

        List<IClasspathAttribute> attributes = new ArrayList<>();
        values.forEach((name, value) -> attributes.add(JavaCore.newClasspathAttribute(name, value)));
        return attributes.toArray(IClasspathAttribute[]::new);
    }

    static IPath optionalOutputPath(IJavaProject project, IClasspathEntry existing, JsonObject arguments) {
        String output = arguments.has("outputFolder") && !arguments.get("outputFolder").isJsonNull()
                ? arguments.get("outputFolder").getAsString().trim()
                : "";
        if (!output.isEmpty()) {
            String relative = relativePath(arguments, "outputFolder");
            return workspacePath(project, relative);
        }
        return existing == null ? null : existing.getOutputLocation();
    }

    static String json(JsonObject result) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    static IClasspathEntry[] replaceSourceEntry(IClasspathEntry[] entries, IPath sourcePath,
            IClasspathEntry replacement) {
        List<IClasspathEntry> updated = new ArrayList<>();
        boolean replaced = false;
        for (IClasspathEntry entry : entries) {
            if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && sourcePath.equals(entry.getPath())) {
                if (!replaced) {
                    updated.add(replacement);
                    replaced = true;
                }
            } else {
                updated.add(entry);
            }
        }
        if (!replaced) {
            updated.add(replacement);
        }
        return updated.toArray(IClasspathEntry[]::new);
    }

    static IClasspathEntry[] removeSourceEntry(IClasspathEntry[] entries, IPath sourcePath) {
        List<IClasspathEntry> updated = new ArrayList<>();
        for (IClasspathEntry entry : entries) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE || !sourcePath.equals(entry.getPath())) {
                updated.add(entry);
            }
        }
        return updated.toArray(IClasspathEntry[]::new);
    }

    static JsonObject baseResult(IJavaProject project, String sourceFolder) {
        JsonObject result = new JsonObject();
        result.addProperty("projectName", project.getElementName());
        result.addProperty("sourceFolder", sourceFolder);
        result.addProperty("workspacePath", workspacePath(project, sourceFolder).toString());
        return result;
    }

}
