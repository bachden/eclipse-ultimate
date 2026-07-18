package nhb.eclipse.plugin.mcp.ultimate.server;

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

        Entry(Instant timestamp, String remoteAddress, String detail, boolean success) {
            this.timestamp = timestamp;
            this.remoteAddress = remoteAddress;
            this.detail = detail;
            this.success = success;
        }
    }

    private static final int MAX_ENTRIES = 200;

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(String remoteAddress, String detail, boolean success) {
        entries.addLast(new Entry(Instant.now(), remoteAddress, detail, success));
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
