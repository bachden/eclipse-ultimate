package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.net.URI;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns Eclipse and Java metadata for one workspace project. */
public class GetProjectDetailsTool implements McpTool {

    @Override
    public String name() {
        return "get_project_details";
    }

    @Override
    public String description() {
        return "Get detailed metadata for one Eclipse project: state, location, charset, natures, builders, "
                + "project references, and Java source/compiler settings when applicable.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The name of the project to inspect");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        IProject project = Workspace.project(projectName);
        IProjectDescription description = project.getDescription();

        JsonObject details = new JsonObject();
        details.addProperty("name", project.getName());
        details.addProperty("open", project.isOpen());
        details.addProperty("accessible", project.isAccessible());
        details.addProperty("workspacePath", project.getFullPath().toString());

        URI location = project.getLocationURI();
        if (location != null) {
            details.addProperty("location", location.toString());
        }

        String comment = description.getComment();
        if (comment != null && !comment.isBlank()) {
            details.addProperty("comment", comment);
        }

        details.add("natures", strings(description.getNatureIds()));
        details.add("builders", builders(description.getBuildSpec()));
        details.add("referencedProjects", projectNames(project.getReferencedProjects()));
        details.add("referencingProjects", projectNames(project.getReferencingProjects()));

        if (project.isAccessible()) {
            details.addProperty("defaultCharset", project.getDefaultCharset());
            IJavaProject javaProject = JavaCore.create(project);
            boolean isJavaProject = javaProject != null && javaProject.exists();
            details.addProperty("javaProject", isJavaProject);
            if (isJavaProject) {
                details.add("java", javaDetails(javaProject));
            }
        } else {
            details.addProperty("javaProject", false);
        }

        return new GsonBuilder().setPrettyPrinting().create().toJson(details);
    }

    private JsonObject javaDetails(IJavaProject javaProject) throws Exception {
        JsonObject java = new JsonObject();
        java.addProperty("outputLocation", javaProject.getOutputLocation().toString());
        java.addProperty("sourceLevel", javaProject.getOption(JavaCore.COMPILER_SOURCE, true));
        java.addProperty("complianceLevel", javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true));
        java.addProperty("targetLevel", javaProject.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, true));

        JsonArray sourceFolders = new JsonArray();
        for (IClasspathEntry entry : javaProject.getRawClasspath()) {
            if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
                sourceFolders.add(entry.getPath().toString());
            }
        }
        java.add("sourceFolders", sourceFolders);
        return java;
    }

    private JsonArray builders(ICommand[] commands) {
        JsonArray builders = new JsonArray();
        for (ICommand command : commands) {
            builders.add(command.getBuilderName());
        }
        return builders;
    }

    private JsonArray projectNames(IProject[] projects) {
        JsonArray names = new JsonArray();
        for (IProject project : projects) {
            names.add(project.getName());
        }
        return names;
    }

    private JsonArray strings(String[] values) {
        JsonArray result = new JsonArray();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }
}
