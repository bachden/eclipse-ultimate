package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Opens a file in the Eclipse editor, so the developer can see what the agent is looking at. */
public class OpenFileTool implements McpTool {

    @Override
    public String name() {
        return "open_file";
    }

    @Override
    public String description() {
        return "Open a file in the Eclipse editor, given a project and a path relative to its root.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        return Schemas.required(schema, "projectName", "filePath");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");

        IProject project = Workspace.project(projectName);
        IResource resource = Workspace.resource(project, filePath);
        if (!(resource instanceof IFile)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }
        IFile file = (IFile) resource;

        Exception[] failure = new Exception[1];
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                IWorkbenchPage page = window != null ? window.getActivePage() : null;
                if (page != null) {
                    IDE.openEditor(page, file);
                }
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return "Opened " + filePath;
    }
}
