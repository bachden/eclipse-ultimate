package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Finds all references to a Java type or (type + method) using the JDT search engine. */
public class FindReferencesTool implements McpTool {

    @Override
    public String name() {
        return "find_references";
    }

    @Override
    public String description() {
        return "Find all references to a Java type, or to a specific method on that type, across the whole "
                + "workspace using the JDT search engine.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The Java project the type belongs to");
        Schemas.prop(schema, "fqName", "string", "Fully-qualified type name, e.g. com.example.Foo");
        Schemas.prop(schema, "methodName", "string", "Optional method name to search references of, instead of the type itself");
        Schemas.prop(schema, "maxResults", "integer", "Maximum number of results to return (default 200)");
        return Schemas.required(schema, "projectName", "fqName");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String fqName = Schemas.requireString(arguments, "fqName");
        String methodName = Schemas.optString(arguments, "methodName", null);
        int maxResults = Schemas.optInt(arguments, "maxResults", 200);

        IType type = JdtLookup.findType(projectName, fqName);
        IJavaElement target = type;
        if (methodName != null) {
            IMethod found = null;
            for (IMethod method : type.getMethods()) {
                if (method.getElementName().equals(methodName)) {
                    found = method;
                    break;
                }
            }
            if (found == null) {
                throw new IllegalArgumentException("Method not found: " + methodName + " on " + fqName);
            }
            target = found;
        }

        SearchPattern pattern = SearchPattern.createPattern(target, IJavaSearchConstants.REFERENCES);
        SearchEngine engine = new SearchEngine();
        JsonArray results = new JsonArray();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                if (results.size() >= maxResults) {
                    return;
                }
                Object element = match.getElement();
                IResource resource = match.getResource();
                JsonObject entry = new JsonObject();
                entry.addProperty("path", resource instanceof IFile ? resource.getFullPath().toString() : String.valueOf(resource));
                entry.addProperty("offset", match.getOffset());
                entry.addProperty("length", match.getLength());
                entry.addProperty("in", element instanceof IJavaElement ? ((IJavaElement) element).getElementName() : String.valueOf(element));
                results.add(entry);
            }
        };

        IProgressMonitor monitor = new NullProgressMonitor();
        engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                org.eclipse.jdt.core.search.SearchEngine.createWorkspaceScope(), requestor, monitor);

        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
