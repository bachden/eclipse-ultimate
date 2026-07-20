package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Organizes a Java file's imports using JDT's public {@link ImportRewrite}:
 * keeps existing resolvable imports, adds one for every resolvable simple type
 * reference in the file, sorts and dedupes. Unlike Eclipse's Ctrl+Shift+O, it
 * does not prompt to disambiguate a simple name that resolves to multiple
 * candidates — the AST binding JDT already picked at parse time wins.
 */
public class OrganizeImportsTool implements McpTool {

    @Override
    public String name() {
        return "organize_imports";
    }

    @Override
    public String description() {
        return "Organize a Java file's imports (Ctrl+Shift+O): sorts them, removes unused ones, and adds missing "
                + "ones for type references JDT can already resolve.";
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
        ICompilationUnit unit = (ICompilationUnit) JavaCore.create((IFile) resource);
        if (unit == null) {
            throw new IllegalArgumentException(filePath + " is not a Java compilation unit");
        }

        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

        ImportRewrite importRewrite = ImportRewrite.create(astRoot, true);
        astRoot.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleType node) {
                ITypeBinding binding = node.resolveBinding();
                if (binding != null) {
                    IJavaElement javaElement = binding.getTypeDeclaration().getJavaElement();
                    if (javaElement == null || !unit.equals(javaElement.getAncestor(IJavaElement.COMPILATION_UNIT))) {
                        importRewrite.addImport(binding);
                    }
                }
                return true;
            }
        });

        TextEdit edit = importRewrite.rewriteImports(new NullProgressMonitor());
        IDocument document = new Document(unit.getSource());
        edit.apply(document);
        unit.getBuffer().setContents(document.get());
        unit.save(new NullProgressMonitor(), true);
        return "Organized imports in " + filePath;
    }
}
