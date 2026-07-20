package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylyn.builds.core.IBuild;
import org.eclipse.mylyn.builds.core.IBuildModel;
import org.eclipse.mylyn.builds.core.IBuildPlan;
import org.eclipse.mylyn.builds.core.IBuildServer;
import org.eclipse.mylyn.builds.core.spi.BuildConnector;
import org.eclipse.mylyn.builds.ui.BuildsUi;
import org.eclipse.mylyn.commons.core.operations.OperationUtil;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Reads console output for a cached Mylyn build through its connector. */
public class GetMylynBuildLogTool implements McpTool {

    private record LogText(String text, long totalCharacters, boolean truncated) {
    }

    @Override
    public String name() {
        return "get_mylyn_build_log";
    }

    @Override
    public String description() {
        return "Read console output for the newest matching cached Mylyn build through its build connector. "
                + "Filter by planId/planName or buildNumber; output is bounded by maxChars.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "repositoryUrl", "string", "Exact repository URL returned by list_mylyn_build_servers");
        Schemas.prop(schema, "connectorKind", "string",
                "Optional exact connector kind used to disambiguate servers sharing a URL");
        Schemas.prop(schema, "planId", "string", "Optional exact Mylyn build plan id or URL");
        Schemas.prop(schema, "planName", "string", "Optional case-insensitive substring of the build plan name");
        Schemas.prop(schema, "buildNumber", "integer",
                "Optional exact build number; omit to use the newest matching cached build");
        Schemas.prop(schema, "maxChars", "integer",
                "Maximum log characters to return (default 100000, range 1000-1000000)");
        Schemas.prop(schema, "tail", "boolean",
                "Return the end of a truncated log instead of the beginning (default true)");
        return Schemas.required(schema, "repositoryUrl");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String repositoryUrl = Schemas.requireString(arguments, "repositoryUrl").trim();
        String connectorKind = Schemas.optString(arguments, "connectorKind", "").trim();
        String planId = Schemas.optString(arguments, "planId", "").trim();
        String planName = Schemas.optString(arguments, "planName", "").trim().toLowerCase(Locale.ROOT);
        int buildNumber = Schemas.optInt(arguments, "buildNumber", -1);
        int maxChars = Schemas.optInt(arguments, "maxChars", 100000);
        boolean tail = Schemas.optBoolean(arguments, "tail", true);

        if (buildNumber < -1) {
            throw new IllegalArgumentException("buildNumber must be zero or greater");
        }
        if (maxChars < 1000 || maxChars > 1000000) {
            throw new IllegalArgumentException("maxChars must be between 1000 and 1000000");
        }

        IBuildModel model = BuildsUi.getModel();
        IBuildServer server = findServer(model.getServers(), connectorKind, repositoryUrl);
        IBuild build = findBuild(model.getBuilds(), server, planId, planName, buildNumber);
        BuildConnector connector = BuildsUi.getConnector(server);
        if (connector == null) {
            throw new IllegalStateException("No Mylyn Builds connector is available for " + server.getConnectorKind());
        }

        LogText log;
        try (Reader reader = connector.getBehaviour(server.getLocation()).getConsole(build,
                OperationUtil.convert(new NullProgressMonitor()))) {
            if (reader == null) {
                throw new IllegalStateException("Mylyn connector did not return console output for " + build.getUrl());
            }
            log = readLog(reader, maxChars, tail);
        }

        JsonObject result = new JsonObject();
        result.add("server", MylynJson.buildServer(server));
        IBuildPlan plan = build.getPlan();
        if (plan != null) {
            result.add("plan", MylynJson.plan(plan));
        }
        result.add("build", MylynJson.build(build));
        result.addProperty("segment", tail ? "tail" : "head");
        result.addProperty("totalCharacters", log.totalCharacters());
        result.addProperty("returnedCharacters", log.text().length());
        result.addProperty("truncated", log.truncated());
        result.addProperty("log", log.text());
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

    private IBuild findBuild(List<IBuild> builds, IBuildServer server, String planId, String planName,
            int buildNumber) {
        List<IBuild> matches = new ArrayList<>();
        for (IBuild build : builds) {
            if (!MylynJson.sameServer(build.getServer(), server)) {
                continue;
            }
            IBuildPlan plan = build.getPlan();
            if (!planId.isEmpty() && (plan == null || !planId.equals(plan.getId()))) {
                continue;
            }
            if (!planName.isEmpty() && (plan == null || plan.getName() == null
                    || !plan.getName().toLowerCase(Locale.ROOT).contains(planName))) {
                continue;
            }
            if (buildNumber >= 0 && build.getBuildNumber() != buildNumber) {
                continue;
            }
            matches.add(build);
        }
        matches.sort(Comparator.comparingLong(IBuild::getTimestamp).reversed());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No cached Mylyn build matched planId, planName and buildNumber filters");
        }
        return matches.get(0);
    }

    private LogText readLog(Reader reader, int maxChars, boolean tail) throws IOException {
        char[] buffer = new char[8192];
        StringBuilder output = new StringBuilder(Math.min(maxChars, buffer.length));
        long total = 0;
        boolean truncated = false;
        int count;
        while ((count = reader.read(buffer)) != -1) {
            total += count;
            if (tail) {
                output.append(buffer, 0, count);
                if (output.length() > maxChars) {
                    output.delete(0, output.length() - maxChars);
                    truncated = true;
                }
            } else {
                int remaining = maxChars - output.length();
                if (remaining > 0) {
                    output.append(buffer, 0, Math.min(remaining, count));
                }
                if (count > remaining) {
                    truncated = true;
                }
            }
        }
        return new LogText(output.toString(), total, truncated);
    }
}
