package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.model.IThread;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Steps into the next line of a suspended thread. */
public class StepIntoTool implements McpTool {

    @Override
    public String name() {
        return "step_into";
    }

    @Override
    public String description() {
        return "Step into the next executable line of a suspended thread, given launchIndex and threadIndex.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "launchIndex", "integer", "Index of the launch, from list_active_launches");
        Schemas.prop(schema, "threadIndex", "integer", "Index of the thread within the launch's debug target");
        return Schemas.required(schema, "launchIndex", "threadIndex");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        int launchIndex = Schemas.optInt(arguments, "launchIndex", -1);
        int threadIndex = Schemas.optInt(arguments, "threadIndex", -1);
        IThread thread = DebugHandles.thread(launchIndex, threadIndex);
        if (!thread.canStepInto()) {
            throw new IllegalStateException("Thread cannot step into (not suspended?)");
        }
        thread.stepInto();
        return "Stepped into on thread " + threadIndex + " in launch " + launchIndex;
    }
}
