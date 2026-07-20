package nhb.eclipse.ultimate.mcpserver;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.ResourcesPlugin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Keeps {@code .mcp.json} at the workspace root in sync with this plugin's live HTTP endpoint.
 * Since Eclipse allows only one window per workspace but many workspaces (hence many plugin
 * instances) can run concurrently on random ports, an agent working in a given workspace folder
 * needs the current port written where it will actually look — without any manual copy/paste.
 *
 * <p>Only the {@code mcpServers.eclipse-ultimate} entry is touched; every other entry and every
 * other top-level key in the file is preserved as-is. Failures here (bad permissions, corrupt
 * JSON, no workspace root) are logged and swallowed — they must never prevent the HTTP server
 * itself from starting.
 */
final class McpJsonConfigWriter {

    private static final String ENTRY_KEY = "eclipse-ultimate";
    private static final String FILE_NAME = ".mcp.json";

    private McpJsonConfigWriter() {
    }

    static void write(String host, int port, boolean authEnabled, String token) {
        File target = workspaceRootFile();
        if (target == null) {
            log("No workspace root available; skipped writing " + FILE_NAME);
            return;
        }

        try {
            JsonObject root = readExisting(target);
            JsonObject mcpServers = root.has("mcpServers") && root.get("mcpServers").isJsonObject()
                    ? root.getAsJsonObject("mcpServers")
                    : new JsonObject();
            mcpServers.add(ENTRY_KEY, buildEntry(host, port, authEnabled, token));
            root.add("mcpServers", mcpServers);

            try (FileWriter writer = new FileWriter(target, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
            log("Updated " + target.getAbsolutePath() + " (" + ENTRY_KEY + " -> " + host + ":" + port + ")");
        } catch (Exception e) {
            log("Failed to update " + FILE_NAME + ": " + e.getMessage());
        }
    }

    private static JsonObject buildEntry(String host, int port, boolean authEnabled, String token) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "http");
        entry.addProperty("url", "http://" + host + ":" + port + "/mcp");
        if (authEnabled && token != null && !token.isEmpty()) {
            JsonObject headers = new JsonObject();
            headers.addProperty("Authorization", "Bearer " + token);
            entry.add("headers", headers);
        }
        return entry;
    }

    private static JsonObject readExisting(File target) {
        if (!target.isFile()) {
            return new JsonObject();
        }
        try (FileReader reader = new FileReader(target, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            log(target.getName() + " did not contain a JSON object; starting fresh");
        } catch (IOException | RuntimeException e) {
            log("Could not parse existing " + target.getName() + " (" + e.getMessage() + "); starting fresh");
        }
        return new JsonObject();
    }

    private static File workspaceRootFile() {
        try {
            org.eclipse.core.runtime.IPath location = ResourcesPlugin.getWorkspace().getRoot().getLocation();
            if (location == null) {
                return null;
            }
            return location.append(FILE_NAME).toFile();
        } catch (Exception e) {
            return null;
        }
    }

    private static void log(String message) {
        System.out.println("[eclipse-ultimate-mcp] " + message);
    }
}
