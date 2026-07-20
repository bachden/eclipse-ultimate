package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.core.model.IValue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Returns the stack trace of a suspended thread, including local variables per frame. */
public class GetStackTraceTool implements McpTool {

    @Override
    public String name() {
        return "get_stack_trace";
    }

    @Override
    public String description() {
        return "Get the stack trace of a suspended thread, given launchIndex and threadIndex. Each frame includes "
                + "its type/method/line and local variables with their current values.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "launchIndex", "integer", "Index of the launch, from list_active_launches");
        Schemas.prop(schema, "threadIndex", "integer", "Index of the thread within the launch's debug target");
        Schemas.prop(schema, "includeVariables", "boolean", "Include local variables per frame (default true)");
        return Schemas.required(schema, "launchIndex", "threadIndex");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        int launchIndex = Schemas.optInt(arguments, "launchIndex", -1);
        int threadIndex = Schemas.optInt(arguments, "threadIndex", -1);
        boolean includeVariables = Schemas.optBoolean(arguments, "includeVariables", true);

        IThread thread = DebugHandles.thread(launchIndex, threadIndex);
        if (!thread.isSuspended()) {
            throw new IllegalStateException("Thread is not suspended");
        }

        JsonArray frames = new JsonArray();
        IStackFrame[] stackFrames = thread.getStackFrames();
        for (int i = 0; i < stackFrames.length; i++) {
            IStackFrame frame = stackFrames[i];
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.addProperty("name", frame.getName());
            entry.addProperty("lineNumber", frame.getLineNumber());

            if (includeVariables) {
                JsonArray variables = new JsonArray();
                for (IVariable variable : frame.getVariables()) {
                    JsonObject v = new JsonObject();
                    v.addProperty("name", variable.getName());
                    IValue value = variable.getValue();
                    v.addProperty("value", value != null ? value.getValueString() : "null");
                    variables.add(v);
                }
                entry.add("variables", variables);
            }
            frames.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(frames);
    }
}
