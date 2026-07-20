package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Lists task repositories currently configured in Mylyn. */
public class ListMylynTaskRepositoriesTool implements McpTool {

    @Override
    public String name() {
        return "list_mylyn_task_repositories";
    }

    @Override
    public String description() {
        return "List Mylyn Task Repositories configured in Eclipse, optionally filtered by connector kind. "
                + "Credentials and other secrets are never returned.";
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
        List<TaskRepository> repositories = new ArrayList<>(TasksUi.getRepositoryManager().getAllRepositories());
        repositories.removeIf(
                repository -> !connectorKind.isEmpty() && !connectorKind.equals(repository.getConnectorKind()));
        repositories.sort(Comparator
                .comparing(TaskRepository::getConnectorKind, Comparator.nullsFirst(String::compareToIgnoreCase))
                .thenComparing(TaskRepository::getRepositoryLabel, Comparator.nullsFirst(String::compareToIgnoreCase))
                .thenComparing(TaskRepository::getRepositoryUrl, Comparator.nullsFirst(String::compareToIgnoreCase)));

        JsonArray entries = new JsonArray();
        for (TaskRepository repository : repositories) {
            AbstractRepositoryConnector connector = TasksUi.getRepositoryConnector(repository.getConnectorKind());
            entries.add(MylynJson.taskRepository(repository, connector, false));
        }

        JsonObject result = new JsonObject();
        result.addProperty("repositoryCount", entries.size());
        result.add("repositories", entries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
