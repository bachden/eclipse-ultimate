package nhb.eclipse.ultimate.mcpserver.tools.runner;

import org.eclipse.debug.core.ILaunch;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists all running/debugging launches with their index, mode and termination state. */
public class ListActiveLaunchesTool implements McpTool {

    @Override
    public String name() {
        return "list_active_launches";
    }

    @Override
    public String description() {
        return "List all currently tracked launches (running or recently finished) with their index, launch "
                + "config name, mode (run/debug) and termination state. The index is used by other runner tools.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        ILaunch[] launches = DebugHandles.manager().getLaunches();
        JsonArray results = new JsonArray();
        for (int i = 0; i < launches.length; i++) {
            ILaunch launch = launches[i];
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.addProperty("name",
                    launch.getLaunchConfiguration() != null ? launch.getLaunchConfiguration().getName() : "unknown");
            entry.addProperty("mode", launch.getLaunchMode());
            entry.addProperty("terminated", launch.isTerminated());
            results.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(results);
    }
}
