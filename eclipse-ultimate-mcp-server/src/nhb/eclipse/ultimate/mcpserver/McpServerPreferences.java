package nhb.eclipse.ultimate.mcpserver;

import java.security.SecureRandom;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import nhb.eclipse.ultimate.mcpserver.server.McpHttpServer;

/**
 * Persisted host/port/auth-token for the MCP HTTP server, backed by the
 * workspace instance preference store so changes survive restarts.
 */
public final class McpServerPreferences {

    public static final String NODE = "eclipse-ultimate-mcp-server";
    public static final String PREF_HOST = "host";
    public static final String PREF_PORT = "port";
    public static final String PREF_PORT_MODE = "portMode";
    public static final String PREF_AUTH_ENABLED = "authEnabled";
    public static final String PREF_AUTH_TOKEN = "authToken";
    public static final String PREF_SERVER_ENABLED = "serverEnabled";

    public static final String PORT_MODE_RANDOM = "RANDOM";
    public static final String PORT_MODE_FIXED = "FIXED";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 40;

    private McpServerPreferences() {
    }

    public static String getHost() {
        return node().get(PREF_HOST, McpHttpServer.DEFAULT_HOST);
    }

    public static int getPort() {
        return node().getInt(PREF_PORT, McpHttpServer.DEFAULT_PORT);
    }

    public static void setHost(String host) {
        node().put(PREF_HOST, host);
        flush();
    }

    public static void setPort(int port) {
        node().putInt(PREF_PORT, port);
        flush();
    }

    // Random by default: lets multiple Eclipse instances (one per workspace)
    // run this plugin concurrently without a fixed port collision.
    public static boolean isRandomPort() {
        return PORT_MODE_RANDOM.equals(getPortMode());
    }

    public static String getPortMode() {
        return node().get(PREF_PORT_MODE, PORT_MODE_RANDOM);
    }

    public static void setPortMode(String mode) {
        node().put(PREF_PORT_MODE, mode);
        flush();
    }

    // Auth defaults to ON: unlike a read-mostly DB bridge, this plugin exposes
    // file writes, git mutations and debug/launch control, so an unauthenticated
    // listener is a materially higher risk from the start.
    public static boolean isAuthEnabled() {
        return node().getBoolean(PREF_AUTH_ENABLED, true);
    }

    public static void setAuthEnabled(boolean enabled) {
        node().putBoolean(PREF_AUTH_ENABLED, enabled);
        flush();
    }

    // Enabled by default to preserve the plugin's existing auto-start behaviour.
    // This is the user's desired state, not the server's transient runtime state.
    public static boolean isServerEnabled() {
        return node().getBoolean(PREF_SERVER_ENABLED, true);
    }

    public static void setServerEnabled(boolean enabled) {
        node().putBoolean(PREF_SERVER_ENABLED, enabled);
        flush();
    }

    /** Returns the current token, generating and persisting one on first use. */
    public static String getAuthToken() {
        String token = node().get(PREF_AUTH_TOKEN, null);
        if (token == null || token.isEmpty()) {
            token = generateToken();
            setAuthToken(token);
        }
        return token;
    }

    public static void setAuthToken(String token) {
        node().put(PREF_AUTH_TOKEN, token);
        flush();
    }

    /** Generates a fresh random token; does not persist it. */
    public static String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static IEclipsePreferences node() {
        return InstanceScope.INSTANCE.getNode(NODE);
    }

    private static void flush() {
        try {
            node().flush();
        } catch (Exception e) {
            System.out.println("[eclipse-ultimate-mcp] Failed to persist preferences: " + e.getMessage());
        }
    }
}
