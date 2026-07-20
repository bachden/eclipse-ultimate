package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Runs Eclipse JDT Clean Up using the active project/workspace cleanup profile.
 */
public class CleanUpTool implements McpTool {

    @Override
    public String name() {
        return "clean_up";
    }

    @Override
    public String description() {
        return "Run Eclipse JDT Clean Up using the active project/workspace Clean Up profile. Supply filePath for "
                + "one Java file, or omit it to clean every source compilation unit in the Java project.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project to clean");
        Schemas.prop(schema, "filePath", "string",
                "Optional path to one .java file; omit to clean the complete Java project");
        return Schemas.required(schema, "projectName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.optString(arguments, "filePath", "").trim();

        IProject project = CoderResources.project(projectName);
        IJavaProject javaProject = JavaCore.create(project);
        if (javaProject == null || !javaProject.exists()) {
            throw new IllegalArgumentException(projectName + " is not a Java project");
        }

        List<ICompilationUnit> units = filePath.isEmpty() ? projectUnits(javaProject)
                : List.of(compilationUnit(project, filePath));
        if (units.isEmpty()) {
            throw new IllegalArgumentException("No Java source files found in project " + projectName);
        }

        Map<ICompilationUnit, String> beforeSources = new LinkedHashMap<>();
        for (ICompilationUnit unit : units) {
            beforeSources.put(unit, unit.getSource());
        }

        CleanUpRefactoring refactoring = new CleanUpRefactoring("Clean Up via Eclipse Ultimate MCP");
        refactoring.setUseOptionsFromProfile(true);
        for (ICompilationUnit unit : units) {
            refactoring.addCompilationUnit(unit);
        }

        ICleanUp[] cleanUps = JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
        if (cleanUps.length == 0) {
            throw new IllegalStateException("Eclipse JDT did not provide any Clean Up implementations");
        }
        for (ICleanUp cleanUp : cleanUps) {
            refactoring.addCleanUp(cleanUp);
        }

        RefactorSupport.run(refactoring);

        JsonArray modifiedFiles = new JsonArray();
        for (Map.Entry<ICompilationUnit, String> entry : beforeSources.entrySet()) {
            ICompilationUnit unit = entry.getKey();
            if (!entry.getValue().equals(unit.getSource())) {
                modifiedFiles.add(unit.getResource().getProjectRelativePath().toString());
            }
        }

        Set<String> enabledSteps = new LinkedHashSet<>();
        for (ICleanUp cleanUp : cleanUps) {
            String[] descriptions = cleanUp.getStepDescriptions();
            if (descriptions != null) {
                Arrays.stream(descriptions).filter(description -> description != null && !description.isBlank())
                        .forEach(enabledSteps::add);
            }
        }
        JsonArray steps = new JsonArray();
        enabledSteps.forEach(steps::add);

        JsonObject result = new JsonObject();
        result.addProperty("project", projectName);
        result.addProperty("scope", filePath.isEmpty() ? "project" : "file");
        if (!filePath.isEmpty()) {
            result.addProperty("file", filePath);
        }
        result.addProperty("profile", "active Eclipse Clean Up profile");
        result.addProperty("targetCount", units.size());
        result.addProperty("modifiedCount", modifiedFiles.size());
        result.add("modifiedFiles", modifiedFiles);
        result.add("enabledSteps", steps);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private ICompilationUnit compilationUnit(IProject project, String filePath) {
        IResource resource = CoderResources.resource(project, filePath);
        if (!(resource instanceof IFile file)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }
        ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
        if (unit == null || !unit.exists()) {
            throw new IllegalArgumentException(filePath + " is not a Java compilation unit");
        }
        return unit;
    }

    private List<ICompilationUnit> projectUnits(IJavaProject javaProject) throws Exception {
        List<ICompilationUnit> units = new ArrayList<>();
        for (IPackageFragment fragment : javaProject.getPackageFragments()) {
            if (fragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                units.addAll(Arrays.asList(fragment.getCompilationUnits()));
            }
        }
        units.sort((left, right) -> left.getPath().toString().compareTo(right.getPath().toString()));
        return units;
    }
}
