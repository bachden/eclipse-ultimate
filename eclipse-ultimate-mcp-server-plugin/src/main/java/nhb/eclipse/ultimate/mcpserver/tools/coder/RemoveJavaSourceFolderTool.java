package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Removes a Java source-folder entry without deleting its workspace folder. */
public class RemoveJavaSourceFolderTool implements McpTool {

    @Override
    public String name() {
        return "remove_java_source_folder";
    }

    @Override
    public String description() {
        return "Remove a project-relative source folder from the Java build path without deleting the folder or its files.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project to configure");
        Schemas.prop(schema, "sourceFolder", "string", "Project-relative source folder to remove");
        Schemas.prop(schema, "failIfMissing", "boolean",
                "Fail when the folder is not currently a source entry (default false)");
        return Schemas.required(schema, "projectName", "sourceFolder");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String sourceFolder = JavaProjectBuildPathSupport.relativePath(arguments, "sourceFolder");
        boolean failIfMissing = Schemas.optBoolean(arguments, "failIfMissing", false);

        IJavaProject project = JavaProjectBuildPathSupport.javaProject(projectName);
        IPath sourcePath = JavaProjectBuildPathSupport.workspacePath(project, sourceFolder);
        IClasspathEntry[] entries = project.getRawClasspath();
        IClasspathEntry existing = JavaProjectBuildPathSupport.sourceEntry(entries, sourcePath);
        if (existing == null && failIfMissing) {
            throw new IllegalArgumentException("Source folder is not on the Java build path: " + sourceFolder);
        }
        if (existing != null) {
            project.setRawClasspath(JavaProjectBuildPathSupport.removeSourceEntry(entries, sourcePath),
                    new NullProgressMonitor());
        }

        JsonObject result = JavaProjectBuildPathSupport.baseResult(project, sourceFolder);
        result.addProperty("removed", existing != null);
        result.addProperty("folderPreserved", true);
        return JavaProjectBuildPathSupport.json(result);
    }
}
