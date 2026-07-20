package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Formats an entire Java file using the JDT code formatter (equivalent to Ctrl+Shift+F) and saves it. */
public class FormatCodeTool implements McpTool {

    @Override
    public String name() {
        return "format_code";
    }

    @Override
    public String description() {
        return "Format an entire Java file using Eclipse's code formatter (Ctrl+Shift+F) and save it to disk.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the .java file, relative to the project root");
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

        ICompilationUnit unit = (ICompilationUnit) org.eclipse.jdt.core.JavaCore.create(file);
        if (unit == null) {
            throw new IllegalArgumentException(filePath + " is not a Java compilation unit");
        }

        String source = unit.getSource();
        Map<String, String> options = JavaCore.getOptions();
        CodeFormatter formatter = org.eclipse.jdt.core.ToolFactory.createCodeFormatter(options);
        TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, source,
                0, source.length(), 0, null);
        if (edit == null) {
            throw new IllegalStateException("Formatter could not parse " + filePath + " (syntax errors?)");
        }

        IDocument document = new Document(source);
        edit.apply(document);
        String formatted = document.get();

        file.setContents(new ByteArrayInputStream(formatted.getBytes(StandardCharsets.UTF_8)), true, true,
                new NullProgressMonitor());
        return "Formatted " + filePath;
    }
}
