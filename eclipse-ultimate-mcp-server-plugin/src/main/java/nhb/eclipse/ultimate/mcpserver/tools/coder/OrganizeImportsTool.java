package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Organizes a Java file's imports using JDT's public operation. The edit is
 * committed through the shared text buffer so an open editor cannot remain
 * stale or silently discard the result.
 */
public class OrganizeImportsTool implements McpTool {

    @Override
    public String name() {
        return "organize_imports";
    }

    @Override
    public String description() {
        return "Organize a Java file's imports (Ctrl+Shift+O): removes unused imports, adds resolvable missing "
                + "imports, sorts them, and commits the result through Eclipse's shared text buffer.";
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

        IProject project = CoderResources.project(projectName);
        IResource resource = CoderResources.resource(project, filePath);
        if (!(resource instanceof IFile)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }
        IFile file = (IFile) resource;
        ICompilationUnit unit = (ICompilationUnit) JavaCore.create(file);
        if (unit == null) {
            throw new IllegalArgumentException(filePath + " is not a Java compilation unit");
        }

        TextFiles.EditResult edit = TextFiles.edit(file, content -> {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(unit);
            parser.setResolveBindings(true);
            CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

            OrganizeImportsOperation operation = new OrganizeImportsOperation(unit, astRoot, false, false, true, null,
                    false);
            TextEdit textEdit = operation.createTextEdit(new NullProgressMonitor());
            Document document = new Document(content);
            textEdit.apply(document);
            return document.get();
        });

        return edit.changed() ? "Organized imports in " + filePath : "Imports already organized in " + filePath;
    }
}
