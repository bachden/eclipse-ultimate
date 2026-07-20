package nhb.eclipse.ultimate.mcpserver.ui;

/**
 * Generates ready-to-paste MCP client configuration snippets for popular
 * agents, pointed at this plugin's HTTP endpoint. When auth is enabled, the
 * generated snippet also carries the bearer token in whatever form that agent
 * expects.
 */
public final class McpConfigGenerator {

    /** One target agent/client we know how to generate a config snippet for. */
    public enum Agent {
        CLAUDE_CODE_CLI("Claude Code (CLI)"),
        CLAUDE_DESKTOP("Claude Desktop (claude_desktop_config.json)"),
        CODEX_CLI("Codex CLI (config.toml)"),
        RAW_URL("Raw endpoint URL");

        private final String label;

        Agent(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private McpConfigGenerator() {
    }

    public static String generate(Agent agent, String host, int port, boolean authEnabled, String token) {
        String url = endpointUrl(host, port);
        boolean withAuth = authEnabled && token != null && !token.isEmpty();
        switch (agent) {
        case CLAUDE_CODE_CLI:
            return withAuth
                    ? "claude mcp add --transport http eclipse-ultimate " + url + " --header \"Authorization: Bearer "
                            + token + "\""
                    : "claude mcp add --transport http eclipse-ultimate " + url;
        case CLAUDE_DESKTOP:
            return withAuth ? "{\n" + "  \"mcpServers\": {\n" + "    \"eclipse-ultimate\": {\n"
                    + "      \"type\": \"http\",\n" + "      \"url\": \"" + url + "\",\n" + "      \"headers\": {\n"
                    + "        \"Authorization\": \"Bearer " + token + "\"\n" + "      }\n" + "    }\n" + "  }\n" + "}"
                    : "{\n" + "  \"mcpServers\": {\n" + "    \"eclipse-ultimate\": {\n" + "      \"type\": \"http\",\n"
                            + "      \"url\": \"" + url + "\"\n" + "    }\n" + "  }\n" + "}";
        case CODEX_CLI:
            return withAuth
                    ? "[mcp_servers.eclipse-ultimate]\n" + "url = \"" + url + "\"\n"
                            + "http_headers = { Authorization = \"Bearer " + token + "\" }"
                    : "[mcp_servers.eclipse-ultimate]\n" + "url = \"" + url + "\"";
        case RAW_URL:
        default:
            return withAuth ? url + "\nAuthorization: Bearer " + token : url;
        }
    }

    private static String endpointUrl(String host, int port) {
        String connectHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        return "http://" + connectHost + ":" + port + "/mcp";
    }
}
