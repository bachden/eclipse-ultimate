package nhb.eclipse.ultimate.mcpserver.tools.runner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jdt.debug.eval.EvaluationManager;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Evaluates a Java expression in the context of a suspended stack frame. */
public class EvaluateExpressionTool implements McpTool {

    @Override
    public String name() {
        return "evaluate_expression";
    }

    @Override
    public String description() {
        return "Evaluate a Java expression in the context of a suspended stack frame, given launchIndex, "
                + "threadIndex, frameIndex and a projectName to resolve types against. Blocks until the "
                + "evaluation completes.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "Java project used to resolve types for the expression");
        Schemas.prop(schema, "launchIndex", "integer", "Index of the launch, from list_active_launches");
        Schemas.prop(schema, "threadIndex", "integer", "Index of the thread within the launch's debug target");
        Schemas.prop(schema, "frameIndex", "integer", "Index of the stack frame (0 = top)");
        Schemas.prop(schema, "expression", "string", "Java expression to evaluate");
        return Schemas.required(schema, "projectName", "launchIndex", "threadIndex", "frameIndex", "expression");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        int launchIndex = Schemas.optInt(arguments, "launchIndex", -1);
        int threadIndex = Schemas.optInt(arguments, "threadIndex", -1);
        int frameIndex = Schemas.optInt(arguments, "frameIndex", 0);
        String expression = Schemas.requireString(arguments, "expression");

        IStackFrame frame = DebugHandles.frame(launchIndex, threadIndex, frameIndex);
        if (!(frame instanceof IJavaStackFrame)) {
            throw new IllegalStateException("Frame is not a Java stack frame");
        }
        IJavaStackFrame javaFrame = (IJavaStackFrame) frame;
        IJavaDebugTarget target = (IJavaDebugTarget) frame.getDebugTarget();

        org.eclipse.core.resources.IProject project = org.eclipse.core.resources.ResourcesPlugin.getWorkspace()
                .getRoot().getProject(projectName);
        IJavaProject javaProject = JavaCore.create(project);
        IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(javaProject, target);

        CountDownLatch latch = new CountDownLatch(1);
        String[] resultText = new String[1];
        String[] errorText = new String[1];
        try {
            engine.evaluate(expression, javaFrame, new IEvaluationListener() {
                @Override
                public void evaluationComplete(IEvaluationResult result) {
                    if (result.hasErrors()) {
                        StringBuilder sb = new StringBuilder();
                        for (String msg : result.getErrorMessages()) {
                            sb.append(msg).append('\n');
                        }
                        errorText[0] = sb.toString();
                    } else {
                        try {
                            resultText[0] = result.getValue() != null ? result.getValue().getValueString() : "null";
                        } catch (Exception e) {
                            errorText[0] = e.getMessage();
                        }
                    }
                    latch.countDown();
                }
            }, org.eclipse.debug.core.DebugEvent.UNSPECIFIED, false);
        } finally {
            // Engine disposal happens after the async evaluation completes; nothing to close here.
        }

        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Evaluation timed out after 30s");
        }
        if (errorText[0] != null) {
            throw new IllegalStateException(errorText[0]);
        }
        return resultText[0];
    }
}
