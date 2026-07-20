package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Adds or updates a Java project's source-folder build-path entry. */
public class ConfigureJavaSourceFolderTool implements McpTool {

    @Override
    public String name() {
        return "configure_java_source_folder";
    }

    @Override
    public String description() {
        return "Add or update a Java source folder in the project build path. Supports test-source marking, "
                + "a separate output folder, linked folders and optional folder creation without changing other classpath entries.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project to configure");
        Schemas.prop(schema, "sourceFolder", "string", "Project-relative source folder, such as src/test/java");
        Schemas.prop(schema, "testSource", "boolean",
                "Mark this entry as test code; omitted preserves an existing value and defaults false for a new entry");
        Schemas.prop(schema, "outputFolder", "string", "Optional project-relative output folder for this source entry");
        Schemas.prop(schema, "createIfMissing", "boolean",
                "Create the source folder when it does not exist (default false)");
        return Schemas.required(schema, "projectName", "sourceFolder");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String sourceFolder = JavaProjectBuildPathSupport.relativePath(arguments, "sourceFolder");
        boolean createIfMissing = Schemas.optBoolean(arguments, "createIfMissing", false);

        IJavaProject project = JavaProjectBuildPathSupport.javaProject(projectName);
        IResource folder = JavaProjectBuildPathSupport.requireFolder(project, sourceFolder, createIfMissing);
        IPath sourcePath = JavaProjectBuildPathSupport.workspacePath(project, sourceFolder);
        IClasspathEntry[] entries = project.getRawClasspath();
        IClasspathEntry existing = JavaProjectBuildPathSupport.sourceEntry(entries, sourcePath);
        boolean testSource = arguments.has("testSource") && !arguments.get("testSource").isJsonNull()
                ? arguments.get("testSource").getAsBoolean()
                : JavaProjectBuildPathSupport.isTestSource(existing);
        IPath outputPath = JavaProjectBuildPathSupport.optionalOutputPath(project, existing, arguments);
        IClasspathEntry replacement = JavaCore.newSourceEntry(sourcePath,
                existing == null ? new IPath[0] : existing.getInclusionPatterns(),
                existing == null ? new IPath[0] : existing.getExclusionPatterns(), outputPath,
                JavaProjectBuildPathSupport.attributes(existing, testSource));

        project.setRawClasspath(JavaProjectBuildPathSupport.replaceSourceEntry(entries, sourcePath, replacement),
                new NullProgressMonitor());

        JsonObject result = JavaProjectBuildPathSupport.baseResult(project, sourceFolder);
        result.addProperty("action", existing == null ? "added" : "updated");
        result.addProperty("testSource", testSource);
        result.addProperty("folderExists", folder.exists());
        if (outputPath != null) {
            result.addProperty("outputFolder",
                    outputPath.removeFirstSegments(project.getPath().segmentCount()).toString());
        }
        return JavaProjectBuildPathSupport.json(result);
    }
}
