package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Sets a Java project's default compiler output folder. */
public class ConfigureJavaOutputFolderTool implements McpTool {

    @Override
    public String name() {
        return "configure_java_output_folder";
    }

    @Override
    public String description() {
        return "Set a Java project's default compiler output folder while preserving per-source output overrides.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project to configure");
        Schemas.prop(schema, "outputFolder", "string",
                "Project-relative compiler output folder, such as target/classes");
        Schemas.prop(schema, "createIfMissing", "boolean",
                "Create the output folder when it does not exist (default false)");
        return Schemas.required(schema, "projectName", "outputFolder");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String outputFolder = JavaProjectBuildPathSupport.relativePath(arguments, "outputFolder");
        boolean createIfMissing = Schemas.optBoolean(arguments, "createIfMissing", false);

        IJavaProject project = JavaProjectBuildPathSupport.javaProject(projectName);
        JavaProjectBuildPathSupport.requireFolder(project, outputFolder, createIfMissing);
        IPath outputPath = JavaProjectBuildPathSupport.workspacePath(project, outputFolder);
        project.setOutputLocation(outputPath, new NullProgressMonitor());

        JsonObject result = new JsonObject();
        result.addProperty("projectName", project.getElementName());
        result.addProperty("outputFolder", outputFolder);
        result.addProperty("workspacePath", outputPath.toString());
        return JavaProjectBuildPathSupport.json(result);
    }
}
