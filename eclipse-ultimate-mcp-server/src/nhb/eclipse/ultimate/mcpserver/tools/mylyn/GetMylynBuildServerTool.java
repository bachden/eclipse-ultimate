package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylyn.builds.core.IBuild;
import org.eclipse.mylyn.builds.core.IBuildModel;
import org.eclipse.mylyn.builds.core.IBuildServer;
import org.eclipse.mylyn.builds.core.IBuildServerConfiguration;
import org.eclipse.mylyn.builds.core.spi.BuildConnector;
import org.eclipse.mylyn.builds.ui.BuildsUi;
import org.eclipse.mylyn.commons.core.operations.OperationUtil;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Reads one Mylyn Builds connection and its plans/builds. */
public class GetMylynBuildServerTool implements McpTool {

    @Override
    public String name() {
        return "get_mylyn_build_server";
    }

    @Override
    public String description() {
        return "Get safe connection metadata, plans and cached builds for one Mylyn Builds server such as Jenkins. "
                + "Set refresh=true to connect to the server and refresh its configuration.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "repositoryUrl", "string", "Exact repository URL returned by list_mylyn_build_servers");
        Schemas.prop(schema, "connectorKind", "string",
                "Optional exact connector kind used to disambiguate servers sharing a URL");
        Schemas.prop(schema, "refresh", "boolean",
                "Connect to the build server and refresh its configuration before returning plans (default false)");
        Schemas.prop(schema, "maxPlans", "integer",
                "Maximum number of plans to return across the plan hierarchy (default 100, maximum 1000)");
        Schemas.prop(schema, "maxBuilds", "integer",
                "Maximum number of cached builds to return (default 20, maximum 200; use 0 to omit)");
        return Schemas.required(schema, "repositoryUrl");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String repositoryUrl = Schemas.requireString(arguments, "repositoryUrl").trim();
        String connectorKind = Schemas.optString(arguments, "connectorKind", "").trim();
        boolean refresh = Schemas.optBoolean(arguments, "refresh", false);
        int maxPlans = Schemas.optInt(arguments, "maxPlans", 100);
        int maxBuilds = Schemas.optInt(arguments, "maxBuilds", 20);
        if (maxPlans < 1 || maxPlans > 1000) {
            throw new IllegalArgumentException("maxPlans must be between 1 and 1000");
        }
        if (maxBuilds < 0 || maxBuilds > 200) {
            throw new IllegalArgumentException("maxBuilds must be between 0 and 200");
        }

        IBuildModel model = BuildsUi.getModel();
        IBuildServer server = findServer(model.getServers(), connectorKind, repositoryUrl);
        BuildConnector connector = BuildsUi.getConnector(server);

        IBuildServerConfiguration configuration;
        String configurationSource;
        if (refresh) {
            if (connector == null) {
                throw new IllegalStateException(
                        "No Mylyn Builds connector is available for " + server.getConnectorKind());
            }
            configuration = connector.getBehaviour(server.getLocation())
                    .refreshConfiguration(OperationUtil.convert(new NullProgressMonitor()));
            configurationSource = "refreshed";
        } else {
            configuration = server.getConfiguration();
            configurationSource = "cached";
        }

        JsonObject result = MylynJson.buildServer(server);
        if (connector != null) {
            result.addProperty("connectorLabel", connector.getLabel());
        }
        result.addProperty("configurationSource", configurationSource);

        JsonArray plans = MylynJson.plans(configuration.getPlans(), maxPlans);
        result.addProperty("returnedPlanCount", plans.size());
        result.add("plans", plans);

        List<IBuild> builds = new ArrayList<>();
        for (IBuild build : model.getBuilds()) {
            if (MylynJson.sameServer(build.getServer(), server)) {
                builds.add(build);
            }
        }
        builds.sort(Comparator.comparingLong(IBuild::getTimestamp).reversed());
        result.addProperty("cachedBuildCount", builds.size());

        JsonArray buildEntries = new JsonArray();
        for (int i = 0; i < Math.min(maxBuilds, builds.size()); i++) {
            buildEntries.add(MylynJson.build(builds.get(i)));
        }
        result.addProperty("returnedBuildCount", buildEntries.size());
        result.add("builds", buildEntries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private IBuildServer findServer(List<IBuildServer> servers, String connectorKind, String repositoryUrl) {
        List<IBuildServer> matches = servers.stream()
                .filter(server -> repositoryUrl.equals(MylynJson.serverUrl(server)))
                .filter(server -> connectorKind.isEmpty() || connectorKind.equals(server.getConnectorKind())).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Mylyn build server selector matched " + matches.size()
                    + " servers; use repositoryUrl and connectorKind from list_mylyn_build_servers");
        }
        return matches.get(0);
    }
}
