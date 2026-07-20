package nhb.eclipse.ultimate.mcpserver.mcp;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Shared registry for long-running operations kicked off by tools (Maven
 * builds, test runs, Java application launches, large refactorings). A
 * single registry backs {@code list_operations}, {@code get_operation_status}
 * and {@code cancel_operation} across every tool group, instead of each
 * group tracking its own operations table.
 */
public final class OperationRegistry {

    public enum State {
        RUNNING, DONE, FAILED, CANCELLED
    }

    /** One tracked operation: its kind, current state and output-so-far. */
    public static final class Operation {
        public final String id;
        public final String kind;
        public final Instant startedAt;
        volatile Instant finishedAt;
        volatile State state = State.RUNNING;
        volatile String output = "";
        volatile String error;
        volatile Future<?> future;

        Operation(String id, String kind) {
            this.id = id;
            this.kind = kind;
            this.startedAt = Instant.now();
        }
    }

    private static final OperationRegistry INSTANCE = new OperationRegistry();

    private final Map<String, Operation> operations = new ConcurrentHashMap<>();

    public static OperationRegistry getInstance() {
        return INSTANCE;
    }

    private OperationRegistry() {
    }

    public Operation start(String kind) {
        String id = UUID.randomUUID().toString();
        Operation op = new Operation(id, kind);
        operations.put(id, op);
        return op;
    }

    public void attachFuture(Operation op, Future<?> future) {
        op.future = future;
    }

    public void appendOutput(Operation op, String chunk) {
        op.output = op.output + chunk;
    }

    public void complete(Operation op, String finalOutput) {
        op.output = finalOutput != null ? finalOutput : op.output;
        op.state = State.DONE;
        op.finishedAt = Instant.now();
    }

    public void fail(Operation op, String error) {
        op.error = error;
        op.state = State.FAILED;
        op.finishedAt = Instant.now();
    }

    public boolean cancel(String id) {
        Operation op = operations.get(id);
        if (op == null || op.state != State.RUNNING) {
            return false;
        }
        boolean cancelled = op.future == null || op.future.cancel(true);
        if (cancelled) {
            op.state = State.CANCELLED;
            op.finishedAt = Instant.now();
        }
        return cancelled;
    }

    public Operation get(String id) {
        return operations.get(id);
    }

    public Collection<Operation> list() {
        return operations.values();
    }
}
