package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IFile;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns the path of the file currently active in the Eclipse editor, if any. */
public class GetCurrentlyOpenedFileTool implements McpTool {

    @Override
    public String name() {
        return "get_currently_opened_file";
    }

    @Override
    public String description() {
        return "Get the workspace-relative path of the file currently active in the Eclipse editor. Call this "
                + "first whenever the user refers to \"this file\" or \"this class\".";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String[] result = new String[1];
        Display display = Display.getDefault();
        display.syncExec(() -> {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return;
            }
            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return;
            }
            IEditorPart editor = page.getActiveEditor();
            if (editor == null) {
                return;
            }
            IEditorInput input = editor.getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFile file = ((IFileEditorInput) input).getFile();
                result[0] = file.getFullPath().toString();
            }
        });
        if (result[0] == null) {
            return "No file is currently open in the editor.";
        }
        return result[0];
    }
}
