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

        Entry(Instant timestamp, String remoteAddress, String detail, boolean success, long durationMillis) {
            this.timestamp = timestamp;
            this.remoteAddress = remoteAddress;
            this.detail = detail;
            this.success = success;
            this.durationMillis = durationMillis;
        }
    }

    private static final int MAX_ENTRIES = 200;

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(String remoteAddress, String detail, boolean success) {
        record(remoteAddress, detail, success, -1);
    }

    public void record(String remoteAddress, String detail, boolean success, long durationMillis) {
        entries.addLast(new Entry(Instant.now(), remoteAddress, detail, success, durationMillis));
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
