package nhb.eclipse.ultimate.mcpserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Keeps the Eclipse workspace's project-scoped Codex MCP configuration in sync
 * with this plugin's live HTTP endpoint.
 *
 * <p>
 * Only the managed {@code mcp_servers.eclipse-ultimate} URL and static
 * authorization header are changed. Other Codex settings and other properties
 * on the same MCP server are preserved.
 */
final class CodexConfigWriter {

    private static final String DIRECTORY_NAME = ".codex";
    private static final String FILE_NAME = "config.toml";
    private static final String TABLE_HEADER = "[mcp_servers.eclipse-ultimate]";
    private static final Pattern SERVER_TABLE = Pattern.compile(
            "^\\s*\\[\\s*mcp_servers\\s*\\.\\s*(?:eclipse-ultimate|\\\"eclipse-ultimate\\\"|'eclipse-ultimate')\\s*\\]\\s*(?:#.*)?$");
    private static final Pattern LEGACY_HEADERS_TABLE = Pattern.compile(
            "^\\s*\\[\\s*mcp_servers\\s*\\.\\s*(?:eclipse-ultimate|\\\"eclipse-ultimate\\\"|'eclipse-ultimate')"
                    + "\\s*\\.\\s*headers\\s*\\]\\s*(?:#.*)?$");
    private static final Pattern TABLE = Pattern.compile("^\\s*\\[\\[?.+\\]\\]?\\s*(?:#.*)?$");

    private CodexConfigWriter() {
    }

    static void write(String host, int port, boolean authEnabled, String token) {
        File workspaceRoot = workspaceRootFile();
        if (workspaceRoot == null) {
            log("No workspace root available; skipped writing " + DIRECTORY_NAME + "/" + FILE_NAME);
            return;
        }

        File directory = new File(workspaceRoot, DIRECTORY_NAME);
        File target = new File(directory, FILE_NAME);
        try {
            Files.createDirectories(directory.toPath());
            String existing = target.isFile() ? Files.readString(target.toPath(), StandardCharsets.UTF_8) : "";
            String updated = updateContent(existing, endpointUrl(host, port), authEnabled, token);
            Files.writeString(target.toPath(), updated, StandardCharsets.UTF_8);
            log("Updated " + target.getAbsolutePath() + " (eclipse-ultimate -> " + host + ":" + port + ")");
        } catch (IOException | RuntimeException e) {
            log("Failed to update " + DIRECTORY_NAME + "/" + FILE_NAME + ": " + e.getMessage());
        }
    }

    static String updateContent(String existing, String url, boolean authEnabled, String token) {
        String lineSeparator = existing.contains("\r\n") ? "\r\n" : System.lineSeparator();
        List<String> lines = new ArrayList<>();
        existing.lines().forEach(lines::add);

        removeLegacyHeadersTables(lines);

        int tableStart = findTable(lines, SERVER_TABLE);
        if (tableStart < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add(TABLE_HEADER);
            tableStart = lines.size() - 1;
        }

        setProperty(lines, tableStart, "url", tomlString(url), "url");
        String authorization = authEnabled && token != null && !token.isEmpty()
                ? "{ Authorization = " + tomlString("Bearer " + token) + " }"
                : null;
        setProperty(lines, tableStart, "http_headers", authorization, "url");

        return String.join(lineSeparator, lines) + lineSeparator;
    }

    private static void removeLegacyHeadersTables(List<String> lines) {
        for (int index = 0; index < lines.size();) {
            if (!LEGACY_HEADERS_TABLE.matcher(lines.get(index)).matches()) {
                index++;
                continue;
            }
            int end = findNextTable(lines, index + 1);
            lines.subList(index, end).clear();
        }
    }

    private static int findTable(List<String> lines, Pattern tablePattern) {
        for (int index = 0; index < lines.size(); index++) {
            if (tablePattern.matcher(lines.get(index)).matches()) {
                return index;
            }
        }
        return -1;
    }

    private static int findNextTable(List<String> lines, int start) {
        for (int index = start; index < lines.size(); index++) {
            if (TABLE.matcher(lines.get(index)).matches()) {
                return index;
            }
        }
        return lines.size();
    }

    private static void setProperty(List<String> lines, int tableStart, String key, String value,
            String insertAfterKey) {
        Pattern property = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*=.*$");
        int tableEnd = findNextTable(lines, tableStart + 1);
        int firstMatch = -1;

        for (int index = tableStart + 1; index < tableEnd; index++) {
            if (!property.matcher(lines.get(index)).matches()) {
                continue;
            }
            if (value != null && firstMatch < 0) {
                lines.set(index, key + " = " + value);
                firstMatch = index;
            } else {
                lines.remove(index);
                tableEnd--;
                index--;
            }
        }

        if (value == null || firstMatch >= 0) {
            return;
        }

        int insertionIndex = tableStart + 1;
        if (insertAfterKey != null) {
            Pattern precedingProperty = Pattern.compile("^\\s*" + Pattern.quote(insertAfterKey) + "\\s*=.*$");
            for (int index = tableStart + 1; index < tableEnd; index++) {
                if (precedingProperty.matcher(lines.get(index)).matches()) {
                    insertionIndex = index + 1;
                    break;
                }
            }
        }
        lines.add(insertionIndex, key + " = " + value);
    }

    private static String endpointUrl(String host, int port) {
        String connectHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        return "http://" + connectHost + ":" + port + "/mcp";
    }

    private static String tomlString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
            case '\\':
                escaped.append("\\\\");
                break;
            case '"':
                escaped.append("\\\"");
                break;
            case '\b':
                escaped.append("\\b");
                break;
            case '\t':
                escaped.append("\\t");
                break;
            case '\n':
                escaped.append("\\n");
                break;
            case '\f':
                escaped.append("\\f");
                break;
            case '\r':
                escaped.append("\\r");
                break;
            default:
                if (character < 0x20) {
                    escaped.append(String.format("\\u%04x", (int) character));
                } else {
                    escaped.append(character);
                }
                break;
            }
        }
        return escaped.append('"').toString();
    }

    private static File workspaceRootFile() {
        try {
            org.eclipse.core.runtime.IPath location = ResourcesPlugin.getWorkspace().getRoot().getLocation();
            return location != null ? location.toFile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void log(String message) {
        System.out.println("[eclipse-ultimate-mcp] " + message);
    }
}
