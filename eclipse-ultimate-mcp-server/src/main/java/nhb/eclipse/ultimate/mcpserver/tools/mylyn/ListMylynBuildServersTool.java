package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mylyn.builds.core.IBuild;
import org.eclipse.mylyn.builds.core.IBuildModel;
import org.eclipse.mylyn.builds.core.IBuildPlan;
import org.eclipse.mylyn.builds.core.IBuildServer;
import org.eclipse.mylyn.builds.core.spi.BuildConnector;
import org.eclipse.mylyn.builds.ui.BuildsUi;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists build servers configured in Mylyn Builds, including Jenkins. */
public class ListMylynBuildServersTool implements McpTool {

    @Override
    public String name() {
        return "list_mylyn_build_servers";
    }

    @Override
    public String description() {
        return "List build servers configured in Mylyn Builds (including Jenkins), with connector, connection "
                + "status and cached plan/build counts. Credentials are never returned.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "connectorKind", "string", "Optional exact connector kind, as returned by this tool");
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) {
        String connectorKind = Schemas.optString(arguments, "connectorKind", "").trim();
        IBuildModel model = BuildsUi.getModel();
        List<IBuildServer> servers = new ArrayList<>(model.getServers());
        servers.removeIf(server -> !connectorKind.isEmpty() && !connectorKind.equals(server.getConnectorKind()));
        servers.sort(
                Comparator.comparing(IBuildServer::getConnectorKind, Comparator.nullsFirst(String::compareToIgnoreCase))
                        .thenComparing(IBuildServer::getName, Comparator.nullsFirst(String::compareToIgnoreCase))
                        .thenComparing(MylynJson::serverUrl, Comparator.nullsFirst(String::compareToIgnoreCase)));

        JsonArray entries = new JsonArray();
        for (IBuildServer server : servers) {
            JsonObject entry = MylynJson.buildServer(server);
            BuildConnector connector = BuildsUi.getConnector(server);
            if (connector != null) {
                entry.addProperty("connectorLabel", connector.getLabel());
            }
            entry.addProperty("cachedPlanCount", countPlans(model.getPlans(), server));
            entry.addProperty("cachedBuildCount", countBuilds(model.getBuilds(), server));
            entries.add(entry);
        }

        JsonObject result = new JsonObject();
        result.addProperty("serverCount", entries.size());
        result.add("servers", entries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private long countPlans(List<IBuildPlan> plans, IBuildServer server) {
        return plans.stream().filter(plan -> MylynJson.sameServer(plan.getServer(), server)).count();
    }

    private long countBuilds(List<IBuild> builds, IBuildServer server) {
        return builds.stream().filter(build -> MylynJson.sameServer(build.getServer(), server)).count();
    }
}
