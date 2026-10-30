package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An Owner-declared command boundary and its explicitly created callback targets.
 * The declaration comes from the actual business method and Owner data, never copied from a verified frame.
 * It is not an authorization grant: permissions, object state, versions and execution locks are still checked.
 */
public final class ProjectOwnerOperationScope implements AutoCloseable {
    public record Declaration(Long tenantId, Long actorId, Long projectId, String ownerContext,
            String objectType, String operationCode, int operationVersion, String sourceObjectId,
            ProjectBusinessExecutionSelection execution) {
        public Declaration {
            if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0 || projectId == null || projectId <= 0
                    || blank(ownerContext) || blank(objectType) || blank(operationCode) || operationVersion <= 0
                    || sourceObjectId != null && (sourceObjectId.isBlank() || sourceObjectId.length() > 128))
                throw new IllegalArgumentException("OWNER_OPERATION_DECLARATION_INVALID");
        }
    }

    private static final ThreadLocal<Deque<ProjectOwnerOperationScope>> SCOPES = new ThreadLocal<>();
    private final Declaration declaration;
    private final ProjectVerifiedOperationScope.Frame verified;
    private final Thread thread = Thread.currentThread();
    private final Set<String> created = new HashSet<>();
    private boolean closed;

    private ProjectOwnerOperationScope(Declaration value) {
        declaration = Objects.requireNonNull(value);
        var frame = ProjectVerifiedOperationScope.current();
        verified = sameDomain(frame, value.ownerContext(), value.objectType()) ? frame : null;
        if (verified != null) {
            if (!ProjectVerifiedOperationScope.matches(value.tenantId(), value.actorId(), value.projectId(),
                    value.ownerContext(), value.objectType(), value.execution(), value.operationCode(),
                    value.operationVersion(), value.sourceObjectId())) throw mismatch();
            var parent = current();
            // A second business command needs its own verified invocation; callbacks do not open another command.
            if (parent != null && parent.verified == verified) throw mismatch();
        }
        var stack = SCOPES.get();
        if (stack == null) { stack = new ArrayDeque<>(); SCOPES.set(stack); }
        stack.push(this);
    }

    public static ProjectOwnerOperationScope open(Declaration declaration) {
        return new ProjectOwnerOperationScope(declaration);
    }

    /** An unmarked standalone command keeps its existing path and does not even resolve a project declaration. */
    public static <T> T call(String owner, String type, Supplier<Declaration> identify, Supplier<T> work) {
        if (!sameDomain(ProjectVerifiedOperationScope.current(), owner, type)) return work.get();
        var declaration = identify.get();
        if (declaration == null || !Objects.equals(owner, declaration.ownerContext())
                || !Objects.equals(type, declaration.objectType())) throw mismatch();
        try (var scope = open(declaration)) {
            scope.requireLive();
            return work.get();
        }
    }

    /** Resolve a callback's omitted selection only after checking its actual Owner identity and object. */
    public static ProjectBusinessExecutionSelection executionFor(Long tenant, Long actor, Long project,
            String owner, String type, String objectId, ProjectBusinessExecutionSelection requested) {
        var scope = controlled(owner, type);
        if (scope == null) return requested;
        scope.requireTarget(tenant, actor, project, owner, type, objectId);
        if (requested != null && !requested.equals(scope.declaration.execution())) throw mismatch();
        return scope.declaration.execution();
    }

    /**
     * Call immediately after a successful Owner insert and before form/file callbacks, in the same transaction.
     * The caller supplies its own operation and actual source/new identifiers; no arbitrary existing target grant.
     */
    public static void registerCreated(Long tenant, Long actor, Long project, String owner, String type,
            String operation, int version, String sourceObjectId, String createdObjectId) {
        var scope = controlled(owner, type);
        if (scope == null) return;
        scope.requireTarget(tenant, actor, project, owner, type, sourceObjectId);
        var d = scope.declaration;
        if (!Objects.equals(d.operationCode(), operation) || d.operationVersion() != version
                || !Objects.equals(d.sourceObjectId(), sourceObjectId) || blank(createdObjectId)
                || createdObjectId.length() > 128 || Objects.equals(sourceObjectId, createdObjectId)) throw mismatch();
        scope.created.add(createdObjectId);
    }

    /** The request keeps the actual mutation target. Its proof relates that target to the declared source. */
    public static WriteRequest writeRequest(Long tenant, Long actor, Long project, String owner, String type,
            String objectId, ProjectBusinessExecutionSelection observed) {
        var scope = controlled(owner, type);
        if (scope == null) return new WriteRequest(project, owner, type, observed);
        scope.requireTarget(tenant, actor, project, owner, type, objectId);
        if (!Objects.equals(observed, scope.declaration.execution())) throw mismatch();
        return new WriteRequest(project, owner, type, observed, scope.declaration.operationCode(),
                scope.declaration.operationVersion(), objectId, new Proof(scope, objectId));
    }

    /** Private construction, same-thread and same-invocation lifetime; never a client DTO or reusable permit. */
    public static final class Proof {
        private final ProjectOwnerOperationScope scope;
        private final String target;
        private Proof(ProjectOwnerOperationScope scope, String target) { this.scope = scope; this.target = target; }

        public boolean matches(Long tenant, Long actor, WriteRequest request) {
            if (request == null || request.ownerProof() != this) return false;
            try {
                scope.requireTarget(tenant, actor, request.projectId(), request.ownerContext(), request.objectType(), request.objectId());
                var d = scope.declaration;
                return Objects.equals(target, request.objectId()) && Objects.equals(d.operationCode(), request.operationCode())
                        && Integer.valueOf(d.operationVersion()).equals(request.operationVersion())
                        && Objects.equals(d.execution(), request.selection());
            } catch (IllegalStateException invalid) { return false; }
        }
    }

    private static ProjectOwnerOperationScope controlled(String owner, String type) {
        var frame = ProjectVerifiedOperationScope.current();
        if (!sameDomain(frame, owner, type)) return null;
        var scope = current();
        if (scope == null || scope.verified != frame) throw mismatch();
        scope.requireLive();
        return scope;
    }

    private static boolean sameDomain(ProjectVerifiedOperationScope.Frame frame, String owner, String type) {
        return frame != null && Objects.equals(frame.ownerContext(), owner) && Objects.equals(frame.objectType(), type);
    }

    private void requireTarget(Long tenant, Long actor, Long project, String owner, String type, String objectId) {
        requireLive();
        if (!Objects.equals(declaration.tenantId(), tenant) || !Objects.equals(declaration.actorId(), actor)
                || !Objects.equals(declaration.projectId(), project) || !Objects.equals(declaration.ownerContext(), owner)
                || !Objects.equals(declaration.objectType(), type)
                || !(Objects.equals(declaration.sourceObjectId(), objectId) || objectId != null && created.contains(objectId)))
            throw mismatch();
    }

    private void requireLive() {
        if (closed || Thread.currentThread() != thread || current() != this || verified == null
                || ProjectVerifiedOperationScope.current() != verified) throw mismatch();
    }

    private static ProjectOwnerOperationScope current() {
        var stack = SCOPES.get();
        return stack == null ? null : stack.peek();
    }

    @Override public void close() {
        if (closed) return;
        var stack = SCOPES.get();
        if (Thread.currentThread() != thread || stack == null || stack.peek() != this) throw mismatch();
        stack.pop();
        if (stack.isEmpty()) SCOPES.remove();
        created.clear();
        closed = true;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static IllegalStateException mismatch() { return new IllegalStateException("OWNER_OPERATION_SCOPE_MISMATCH"); }
}
