package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** In-process transaction context; never serialized or accepted from a request. Exact nested frames, not a skip flag. */
public final class ProjectVerifiedOperationScope implements AutoCloseable {
    public record Frame(Long tenantId, Long actorId, Long projectId, String ownerContext,
            String objectType, String operationCode, String objectId, ProjectBusinessExecutionSelection execution) { }
    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();
    private final Frame frame;
    private final Thread thread;
    private boolean closed;
    private ProjectVerifiedOperationScope(Frame value) {
        frame = Objects.requireNonNull(value); thread = Thread.currentThread();
        Deque<Frame> stack = FRAMES.get();
        if (stack == null) { stack = new ArrayDeque<>(); FRAMES.set(stack); }
        stack.push(frame);
    }
    public static ProjectVerifiedOperationScope open(Frame value) { return new ProjectVerifiedOperationScope(value); }
    public static Frame current() { var stack = FRAMES.get(); return stack == null ? null : stack.peek(); }
    public static boolean matches(Long tenant, Long actor, Long project, String owner, String type,
            ProjectBusinessExecutionSelection execution) {
        var f = current();
        return f != null && Objects.equals(f.tenantId(), tenant) && Objects.equals(f.actorId(), actor)
                && Objects.equals(f.projectId(), project) && Objects.equals(f.ownerContext(), owner)
                && Objects.equals(f.objectType(), type) && execution != null && Objects.equals(f.execution(), execution);
    }
    @Override public void close() {
        if (closed) return;
        var stack = FRAMES.get();
        if (Thread.currentThread() != thread || stack == null || stack.peek() != frame)
            throw new IllegalStateException("OPERATION_SCOPE_ORDER_INVALID");
        stack.pop(); if (stack.isEmpty()) FRAMES.remove(); closed = true;
    }
}
