package nhb.eclipse.ultimate.mcpserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import nhb.eclipse.ultimate.mcpserver.server.McpConnectionLog;
import nhb.eclipse.ultimate.mcpserver.server.McpHttpServer;

/**
 * OSGi lifecycle hook. Holds the singleton MCP HTTP server so it can be stopped
 * cleanly when Eclipse shuts down. The server itself is *started* from
 * {@link StartupHook} (org.eclipse.ui.startup) to guarantee the workbench /
 * workspace is fully initialised first.
 */
public class Activator implements BundleActivator {

    public static final String PLUGIN_ID = "eclipse-ultimate-mcp-server";

    /** Notified whenever the server transitions between running/stopped. */
    public interface ServerStateListener {
        void onServerStateChanged(boolean running);
    }

    private static Activator instance;
    private McpHttpServer server;
    private final List<ServerStateListener> listeners = new CopyOnWriteArrayList<>();

    public static Activator getDefault() {
        return instance;
    }

    @Override
    public void start(BundleContext context) {
        instance = this;
    }

    @Override
    public void stop(BundleContext context) {
        stopServer();
        instance = null;
    }

    public void addServerStateListener(ServerStateListener listener) {
        listeners.add(listener);
    }

    public void removeServerStateListener(ServerStateListener listener) {
        listeners.remove(listener);
    }

    public synchronized void startServer() {
        if (server != null) {
            return;
        }
        McpHttpServer candidate = new McpHttpServer();
        try {
            candidate.setAuth(McpServerPreferences::isAuthEnabled, McpServerPreferences::getAuthToken);
            int requestedPort = McpServerPreferences.isRandomPort() ? 0 : McpServerPreferences.getPort();
            candidate.start(McpServerPreferences.getHost(), requestedPort);
            server = candidate;
            McpJsonConfigWriter.write(candidate.getBoundHost(), candidate.getBoundPort(),
                    McpServerPreferences.isAuthEnabled(), McpServerPreferences.getAuthToken());
            CodexConfigWriter.write(candidate.getBoundHost(), candidate.getBoundPort(),
                    McpServerPreferences.isAuthEnabled(), McpServerPreferences.getAuthToken());
        } catch (RuntimeException e) {
            server = null;
            throw e;
        } finally {
            fireStateChanged();
        }
    }

    public synchronized void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        fireStateChanged();
    }

    public synchronized void restartServer() {
        stopServer();
        startServer();
    }

    public synchronized boolean isServerRunning() {
        return server != null && server.isRunning();
    }

    /**
     * Returns the running server's connection log, or {@code null} if not running.
     */
    public synchronized McpConnectionLog getConnectionLog() {
        return server != null ? server.getConnectionLog() : null;
    }

    /**
     * Returns the actual bound port (may differ from the configured one in random
     * mode), or -1 if not running.
     */
    public synchronized int getBoundPort() {
        return server != null ? server.getBoundPort() : -1;
    }

    /** Returns the actual bound host, or {@code null} if not running. */
    public synchronized String getBoundHost() {
        return server != null ? server.getBoundHost() : null;
    }

    private void fireStateChanged() {
        boolean running = isServerRunning();
        for (ServerStateListener listener : listeners) {
            listener.onServerStateChanged(running);
        }
    }
}
