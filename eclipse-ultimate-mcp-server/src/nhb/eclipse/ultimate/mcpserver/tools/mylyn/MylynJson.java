package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylyn.builds.core.IBuild;
import org.eclipse.mylyn.builds.core.IBuildPlan;
import org.eclipse.mylyn.builds.core.IBuildServer;
import org.eclipse.mylyn.builds.core.ITestResult;
import org.eclipse.mylyn.commons.repositories.core.RepositoryLocation;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.TaskRepository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

final class MylynJson {

    private MylynJson() {
    }

    static JsonObject taskRepository(TaskRepository repository, AbstractRepositoryConnector connector,
            boolean detailed) {
        JsonObject json = new JsonObject();
        addString(json, "connectorKind", repository.getConnectorKind());
        if (connector != null) {
            addString(json, "connectorLabel", connector.getLabel());
            addString(json, "connectorShortLabel", connector.getShortLabel());
        }
        addString(json, "label", repository.getRepositoryLabel());
        addString(json, "repositoryUrl", repository.getRepositoryUrl());
        addString(json, "category", repository.getCategory());
        addString(json, "version", repository.getVersion());
        json.addProperty("offline", repository.isOffline());
        json.add("status", status(repository.getStatus()));

        if (detailed) {
            addString(json, "characterEncoding", repository.getCharacterEncoding());
            addString(json, "timeZoneId", repository.getTimeZoneId());
            addString(json, "synchronizationTimestamp", repository.getSynchronizationTimeStamp());
            addDate(json, "configurationDate", repository.getConfigurationDate());
            addString(json, "userName", repository.getUserName());
            json.addProperty("bugRepository", repository.isBugRepository());
            json.addProperty("defaultProxyEnabled", repository.isDefaultProxyEnabled());
            json.add("properties", sanitizedProperties(repository.getProperties()));

            if (connector != null) {
                JsonObject capabilities = new JsonObject();
                capabilities.addProperty("canCreateNewTask", connector.canCreateNewTask(repository));
                capabilities.addProperty("canCreateTaskFromKey", connector.canCreateTaskFromKey(repository));
                capabilities.addProperty("canQuery", connector.canQuery(repository));
                capabilities.addProperty("userManaged", connector.isUserManaged());
                json.add("capabilities", capabilities);
            }
        }
        return json;
    }

    static JsonObject buildServer(IBuildServer server) {
        JsonObject json = new JsonObject();
        addString(json, "connectorKind", server.getConnectorKind());
        addString(json, "name", server.getName());
        addString(json, "label", server.getLabel());
        addString(json, "repositoryUrl", serverUrl(server));
        addString(json, "url", server.getUrl());
        addDate(json, "refreshDate", server.getRefreshDate());
        json.add("status", status(server.getElementStatus()));

        RepositoryLocation location = server.getLocation();
        if (location != null) {
            JsonObject connection = new JsonObject();
            addString(connection, "id", location.getId());
            addString(connection, "label", location.getLabel());
            addString(connection, "url", location.getUrl());
            addString(connection, "userName", location.getUserName());
            connection.addProperty("offline", location.isOffline());
            connection.addProperty("workingCopy", location.isWorkingCopy());
            connection.add("status", status(location.getStatus()));
            connection.add("properties", sanitizedProperties(location.getProperties()));
            json.add("connection", connection);
        }
        return json;
    }

    static JsonArray plans(List<IBuildPlan> roots, int maxPlans) {
        JsonArray result = new JsonArray();
        ArrayDeque<IBuildPlan> pending = new ArrayDeque<>(roots);
        Set<IBuildPlan> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        while (!pending.isEmpty() && result.size() < maxPlans) {
            IBuildPlan plan = pending.removeFirst();
            if (!visited.add(plan)) {
                continue;
            }
            result.add(plan(plan));

            List<IBuildPlan> children = plan.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                pending.addFirst(children.get(i));
            }
        }
        return result;
    }

    static JsonObject plan(IBuildPlan plan) {
        JsonObject json = new JsonObject();
        addString(json, "id", plan.getId());
        addString(json, "name", plan.getName());
        addString(json, "label", plan.getLabel());
        addString(json, "url", plan.getUrl());
        addString(json, "summary", plan.getSummary());
        addString(json, "description", plan.getDescription());
        addString(json, "info", plan.getInfo());
        if (plan.getParent() != null) {
            addString(json, "parentId", plan.getParent().getId());
        }
        json.addProperty("selected", plan.isSelected());
        json.addProperty("health", plan.getHealth());
        json.addProperty("childrenCount", plan.getChildren().size());
        addEnum(json, "state", plan.getState());
        addEnum(json, "status", plan.getStatus());
        addDate(json, "refreshDate", plan.getRefreshDate());
        json.add("elementStatus", status(plan.getElementStatus()));
        if (plan.getLastBuild() != null) {
            json.add("lastBuild", build(plan.getLastBuild()));
        }
        return json;
    }

    static JsonObject build(IBuild build) {
        JsonObject json = new JsonObject();
        addString(json, "id", build.getId());
        addString(json, "name", build.getName());
        addString(json, "label", build.getLabel());
        addString(json, "displayName", build.getDisplayName());
        addString(json, "url", build.getUrl());
        addString(json, "summary", build.getSummary());
        json.addProperty("buildNumber", build.getBuildNumber());
        if (build.getTimestamp() > 0) {
            json.addProperty("timestamp", Instant.ofEpochMilli(build.getTimestamp()).toString());
        }
        json.addProperty("durationMillis", build.getDuration());
        addEnum(json, "state", build.getState());
        addEnum(json, "status", build.getStatus());
        addDate(json, "refreshDate", build.getRefreshDate());
        json.addProperty("artifactCount", build.getArtifacts().size());
        json.addProperty("culpritCount", build.getCulprits().size());
        json.addProperty("causeCount", build.getCause().size());
        json.add("elementStatus", status(build.getElementStatus()));

        ITestResult tests = build.getTestResult();
        if (tests != null) {
            JsonObject testResult = new JsonObject();
            testResult.addProperty("passed", tests.getPassCount());
            testResult.addProperty("failed", tests.getFailCount());
            testResult.addProperty("errors", tests.getErrorCount());
            testResult.addProperty("ignored", tests.getIgnoredCount());
            testResult.addProperty("durationMillis", tests.getDuration());
            json.add("tests", testResult);
        }
        return json;
    }

    static boolean sameServer(IBuildServer left, IBuildServer right) {
        if (left == right) {
            return true;
        }
        return left != null && right != null && equal(left.getConnectorKind(), right.getConnectorKind())
                && equal(serverUrl(left), serverUrl(right));
    }

    static String serverUrl(IBuildServer server) {
        String url = server.getRepositoryUrl();
        RepositoryLocation location = server.getLocation();
        if ((url == null || url.isBlank()) && location != null) {
            url = location.getUrl();
        }
        if (url == null || url.isBlank()) {
            url = server.getUrl();
        }
        return url;
    }

    static JsonObject status(IStatus status) {
        JsonObject json = new JsonObject();
        if (status == null) {
            json.addProperty("severity", "OK");
            return json;
        }
        json.addProperty("severity", severity(status.getSeverity()));
        json.addProperty("code", status.getCode());
        addString(json, "message", status.getMessage());
        addString(json, "plugin", status.getPlugin());
        return json;
    }

    private static JsonObject sanitizedProperties(Map<String, String> properties) {
        JsonObject json = new JsonObject();
        if (properties == null) {
            return json;
        }
        properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String value = isSensitive(entry.getKey()) ? "<redacted>" : entry.getValue();
            if (value != null) {
                json.addProperty(entry.getKey(), value);
            }
        });
        return json;
    }

    private static boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(".", "");
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("credential") || normalized.contains("privatekey")
                || normalized.contains("accesskey") || normalized.contains("apikey");
    }

    private static String severity(int severity) {
        return switch (severity) {
        case IStatus.ERROR -> "ERROR";
        case IStatus.WARNING -> "WARNING";
        case IStatus.INFO -> "INFO";
        case IStatus.CANCEL -> "CANCEL";
        default -> "OK";
        };
    }

    private static void addDate(JsonObject json, String name, Date value) {
        if (value != null) {
            json.addProperty(name, value.toInstant().toString());
        }
    }

    private static void addEnum(JsonObject json, String name, Enum<?> value) {
        if (value != null) {
            json.addProperty(name, value.name());
        }
    }

    private static void addString(JsonObject json, String name, String value) {
        if (value != null && !value.isBlank()) {
            json.addProperty(name, value);
        }
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
