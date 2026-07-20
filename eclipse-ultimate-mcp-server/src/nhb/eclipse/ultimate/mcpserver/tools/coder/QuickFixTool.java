package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ChangeCorrectionProposal;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Lists or applies Eclipse JDT quick fixes for compiler problems in one Java
 * file.
 */
public class QuickFixTool implements McpTool {

    private record ProblemProposals(IProblem problem, int line, List<IJavaCompletionProposal> proposals) {
    }

    private record ProposalMatch(ProblemProposals problem, int index, IJavaCompletionProposal proposal) {
    }

    @Override
    public String name() {
        return "quick_fix";
    }

    @Override
    public String description() {
        return "List headlessly applicable Eclipse JDT quick fixes for a Java file, or apply exactly one by "
                + "proposalTitle/proposalIndex. Filter by line, problemId, or problemMessage to disambiguate.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the Java file");
        Schemas.prop(schema, "filePath", "string", "Path to the .java file, relative to the project root");
        Schemas.prop(schema, "line", "integer", "Optional 1-based problem line");
        Schemas.prop(schema, "problemId", "integer", "Optional Eclipse JDT problem id");
        Schemas.prop(schema, "problemMessage", "string", "Optional case-insensitive substring of the problem message");
        Schemas.prop(schema, "proposalTitle", "string",
                "Optional exact proposal title to apply; omit both proposal selectors to only list fixes");
        Schemas.prop(schema, "proposalIndex", "integer",
                "Optional zero-based proposal index within one matched problem to apply");
        return Schemas.required(schema, "projectName", "filePath");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        int lineFilter = Schemas.optInt(arguments, "line", -1);
        int problemIdFilter = Schemas.optInt(arguments, "problemId", -1);
        String messageFilter = Schemas.optString(arguments, "problemMessage", "").trim();
        String proposalTitle = Schemas.optString(arguments, "proposalTitle", "").trim();
        int proposalIndex = Schemas.optInt(arguments, "proposalIndex", -1);

        if (lineFilter == 0 || lineFilter < -1) {
            throw new IllegalArgumentException("line must be a positive 1-based line number");
        }
        if (proposalIndex < -1) {
            throw new IllegalArgumentException("proposalIndex must be zero or greater");
        }
        if (!proposalTitle.isEmpty() && proposalIndex >= 0) {
            throw new IllegalArgumentException("Use either proposalTitle or proposalIndex, not both");
        }

        ICompilationUnit unit = compilationUnit(projectName, filePath);
        CompilationUnit astRoot = parse(unit);
        List<ProblemProposals> problems = collectProblems(unit, astRoot, lineFilter, problemIdFilter, messageFilter);

        if (!proposalTitle.isEmpty() || proposalIndex >= 0) {
            ProposalMatch match = selectProposal(problems, proposalTitle, proposalIndex);
            ChangeCorrectionProposal proposal = (ChangeCorrectionProposal) match.proposal();
            RefactorSupport.run(proposal.getChange());

            JsonObject result = new JsonObject();
            result.addProperty("file", filePath);
            result.addProperty("applied", true);
            result.addProperty("proposalTitle", proposal.getDisplayString());
            result.addProperty("proposalIndex", match.index());
            addProblemFields(result, match.problem());
            return new GsonBuilder().setPrettyPrinting().create().toJson(result);
        }

        JsonObject result = new JsonObject();
        result.addProperty("file", filePath);
        result.addProperty("applied", false);
        JsonArray problemArray = new JsonArray();
        for (ProblemProposals problem : problems) {
            JsonObject problemJson = new JsonObject();
            addProblemFields(problemJson, problem);

            JsonArray proposalArray = new JsonArray();
            for (int i = 0; i < problem.proposals().size(); i++) {
                IJavaCompletionProposal proposal = problem.proposals().get(i);
                JsonObject proposalJson = new JsonObject();
                proposalJson.addProperty("index", i);
                proposalJson.addProperty("title", proposal.getDisplayString());
                proposalJson.addProperty("relevance", proposal.getRelevance());
                proposalArray.add(proposalJson);
            }
            problemJson.add("proposals", proposalArray);
            problemArray.add(problemJson);
        }
        result.addProperty("problemCount", problems.size());
        result.add("problems", problemArray);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private ICompilationUnit compilationUnit(String projectName, String filePath) {
        IProject project = CoderResources.project(projectName);
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

    private CompilationUnit parse(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private List<ProblemProposals> collectProblems(ICompilationUnit unit, CompilationUnit astRoot, int lineFilter,
            int problemIdFilter, String messageFilter) {
        List<ProblemProposals> result = new ArrayList<>();
        String normalizedMessage = messageFilter.toLowerCase(Locale.ROOT);

        for (IProblem problem : astRoot.getProblems()) {
            int sourceStart = problem.getSourceStart();
            int line = sourceStart >= 0 ? astRoot.getLineNumber(sourceStart) : -1;
            if (lineFilter > 0 && line != lineFilter) {
                continue;
            }
            if (problemIdFilter >= 0 && problem.getID() != problemIdFilter) {
                continue;
            }
            if (!normalizedMessage.isEmpty()
                    && !problem.getMessage().toLowerCase(Locale.ROOT).contains(normalizedMessage)) {
                continue;
            }

            int length = sourceStart >= 0 ? Math.max(0, problem.getSourceEnd() - sourceStart + 1) : 0;
            AssistContext context = new AssistContext(unit, Math.max(0, sourceStart), length);
            context.setASTRoot(astRoot);

            List<IJavaCompletionProposal> proposals = new ArrayList<>();
            IProblemLocation[] locations = { new ProblemLocation(problem) };
            IStatus status = JavaCorrectionProcessor.collectCorrections(context, locations, proposals);
            if (status.getSeverity() >= IStatus.ERROR) {
                throw new IllegalStateException("Could not collect quick fixes: " + status.getMessage());
            }

            proposals.removeIf(proposal -> !(proposal instanceof ChangeCorrectionProposal));
            proposals.sort(Comparator.comparingInt(IJavaCompletionProposal::getRelevance).reversed()
                    .thenComparing(IJavaCompletionProposal::getDisplayString));
            if (!proposals.isEmpty()) {
                result.add(new ProblemProposals(problem, line, proposals));
            }
        }
        return result;
    }

    private ProposalMatch selectProposal(List<ProblemProposals> problems, String proposalTitle, int proposalIndex) {
        List<ProposalMatch> matches = new ArrayList<>();
        for (ProblemProposals problem : problems) {
            for (int i = 0; i < problem.proposals().size(); i++) {
                IJavaCompletionProposal proposal = problem.proposals().get(i);
                if ((!proposalTitle.isEmpty() && proposalTitle.equals(proposal.getDisplayString()))
                        || (proposalIndex >= 0 && proposalIndex == i)) {
                    matches.add(new ProposalMatch(problem, i, proposal));
                }
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Quick-fix selector matched " + matches.size()
                    + " proposals; refine line, problemId, or problemMessage so exactly one proposal matches");
        }
        return matches.get(0);
    }

    private void addProblemFields(JsonObject json, ProblemProposals problem) {
        IProblem compilerProblem = problem.problem();
        json.addProperty("problemId", compilerProblem.getID());
        json.addProperty("line", problem.line());
        json.addProperty("severity", compilerProblem.isError() ? "ERROR" : "WARNING");
        json.addProperty("message", compilerProblem.getMessage());
        json.addProperty("sourceStart", compilerProblem.getSourceStart());
        json.addProperty("sourceEnd", compilerProblem.getSourceEnd());
    }
}
