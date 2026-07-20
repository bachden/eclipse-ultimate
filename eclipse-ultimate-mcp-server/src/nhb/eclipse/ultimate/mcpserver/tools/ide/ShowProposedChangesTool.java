package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Opens an editable current-vs-proposed comparison in Eclipse's Compare editor.
 */
public class ShowProposedChangesTool implements McpTool {

    @Override
    public String name() {
        return "show_proposed_changes";
    }

    @Override
    public String description() {
        return "Open Eclipse's Compare editor for a file, with the current workspace file on the editable left "
                + "and proposed content on the read-only right. The user can copy selected changes and save them.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "proposedContent", "string", "Complete proposed content for the file");
        Schemas.prop(schema, "title", "string", "Optional title for the Compare editor");
        return Schemas.required(schema, "projectName", "filePath", "proposedContent");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        String proposedContent = Schemas.requireString(arguments, "proposedContent");
        String title = Schemas.optString(arguments, "title", "Proposed changes - " + filePath);

        IProject project = Workspace.project(projectName);
        IResource resource = Workspace.resource(project, filePath);
        if (!(resource instanceof IFile)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }
        IFile file = (IFile) resource;

        CompareConfiguration configuration = new CompareConfiguration();
        configuration.setLeftLabel("Current - " + filePath);
        configuration.setRightLabel("Proposed");
        configuration.setLeftEditable(true);
        configuration.setRightEditable(false);

        CompareEditorInput input = new ProposedCompareInput(configuration, title, file, proposedContent);
        Exception[] failure = new Exception[1];
        Display.getDefault().syncExec(() -> {
            try {
                CompareUI.openCompareEditor(input);
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return "Opened proposed changes for " + filePath;
    }

    private static final class ProposedCompareInput extends CompareEditorInput {

        private final String title;
        private final IFile currentFile;
        private final String proposedContent;

        private ProposedCompareInput(CompareConfiguration configuration, String title, IFile currentFile,
                String proposedContent) {
            super(configuration);
            this.title = title;
            this.currentFile = currentFile;
            this.proposedContent = proposedContent;
        }

        @Override
        protected Object prepareInput(IProgressMonitor monitor) {
            ITypedElement current = new ResourceNode(currentFile);
            ITypedElement proposed = new StringCompareElement(currentFile.getName(), currentFile.getFileExtension(),
                    proposedContent);
            return new DiffNode(current, proposed);
        }

        @Override
        public String getTitle() {
            return title;
        }
    }

    private static final class StringCompareElement implements ITypedElement, IStreamContentAccessor {

        private final String name;
        private final String type;
        private final byte[] content;

        private StringCompareElement(String name, String type, String content) {
            this.name = name;
            this.type = type == null || type.isBlank() ? ITypedElement.TEXT_TYPE : type;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Image getImage() {
            return null;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public InputStream getContents() {
            return new ByteArrayInputStream(content);
        }
    }
}
