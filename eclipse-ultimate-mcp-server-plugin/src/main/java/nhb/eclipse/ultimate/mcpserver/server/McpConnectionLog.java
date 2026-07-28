package nhb.eclipse.ultimate.mcpserver.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Bounded, thread-safe log of recent client connections to the MCP HTTP
 * server, for display in the UI (status bar tooltip / connections view).
 */
public class McpConnectionLog {

    /** One recorded connection attempt. */
    public static final class Entry {
        public final Instant timestamp;
        public final String remoteAddress;
        public final String detail;
        public final boolean success;
        /** Wall-clock time to handle the request, in milliseconds; -1 if not measured. */
        public final long durationMillis;
        /** Raw JSON-RPC request body, for inspection in the UI; null if not captured. */
        public final String requestJson;
        /** Raw JSON-RPC response body, for inspection in the UI; null for notifications or failures. */
        public final String responseJson;

        Entry(Instant timestamp, String remoteAddress, String detail, boolean success, long durationMillis,
                String requestJson, String responseJson) {
            this.timestamp = timestamp;
            this.remoteAddress = remoteAddress;
            this.detail = detail;
            this.success = success;
            this.durationMillis = durationMillis;
            this.requestJson = requestJson;
            this.responseJson = responseJson;
        }
    }

    private static final int MAX_ENTRIES = 200;

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(String remoteAddress, String detail, boolean success) {
        record(remoteAddress, detail, success, -1, null, null);
    }

    public void record(String remoteAddress, String detail, boolean success, long durationMillis) {
        record(remoteAddress, detail, success, durationMillis, null, null);
    }

    public void record(String remoteAddress, String detail, boolean success, long durationMillis,
            String requestJson, String responseJson) {
        entries.addLast(
                new Entry(Instant.now(), remoteAddress, detail, success, durationMillis, requestJson, responseJson));
        while (entries.size() > MAX_ENTRIES) {
            entries.pollFirst();
        }
    }

    /** Returns recent entries, most recent last. */
    public List<Entry> recent() {
        List<Entry> snapshot = new ArrayList<>(entries);
        return Collections.unmodifiableList(snapshot);
    }

    public void clear() {
        entries.clear();
    }
}
