package nhb.eclipse.ultimate.mcpserver.tools.mylyn;

import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/** Reads one configured Mylyn task repository without exposing credentials. */
public class GetMylynTaskRepositoryTool implements McpTool {

    @Override
    public String name() {
        return "get_mylyn_task_repository";
    }

    @Override
    public String description() {
        return "Get safe connection metadata, status and connector capabilities for one configured Mylyn Task "
                + "Repository. Passwords, tokens and other secret properties are redacted.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "connectorKind", "string",
                "Exact connector kind returned by list_mylyn_task_repositories");
        Schemas.prop(schema, "repositoryUrl", "string",
                "Exact repository URL returned by list_mylyn_task_repositories");
        return Schemas.required(schema, "connectorKind", "repositoryUrl");
    }

    @Override
    public String execute(JsonObject arguments) {
        String connectorKind = Schemas.requireString(arguments, "connectorKind").trim();
        String repositoryUrl = Schemas.requireString(arguments, "repositoryUrl").trim();
        TaskRepository repository = TasksUi.getRepositoryManager().getRepository(connectorKind, repositoryUrl);
        if (repository == null) {
            throw new IllegalArgumentException("Mylyn Task Repository not found for connectorKind=" + connectorKind
                    + ", repositoryUrl=" + repositoryUrl);
        }

        AbstractRepositoryConnector connector = TasksUi.getRepositoryConnector(repository.getConnectorKind());
        return new GsonBuilder().setPrettyPrinting().create()
                .toJson(MylynJson.taskRepository(repository, connector, true));
    }
}
