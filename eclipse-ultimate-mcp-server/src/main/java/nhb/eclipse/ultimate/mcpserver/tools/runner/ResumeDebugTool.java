package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.model.IThread;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Resumes a suspended thread. */
public class ResumeDebugTool implements McpTool {

    @Override
    public String name() {
        return "resume_debug";
    }

    @Override
    public String description() {
        return "Resume a suspended thread, given launchIndex (from list_active_launches) and threadIndex.";
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
        if (!thread.canResume()) {
            throw new IllegalStateException("Thread cannot be resumed (not suspended?)");
        }
        thread.resume();
        return "Resumed thread " + threadIndex + " in launch " + launchIndex;
    }
}
