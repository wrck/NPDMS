package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Shared by the JUnit bridge and a real-JDK-only runner. No framework replacements. */
@SuppressWarnings("try") // Resources intentionally delimit lexical scopes and are closed on every path.
public final class ProjectOwnerOperationScopeCases {
    private static int cases;
    private ProjectOwnerOperationScopeCases() { }
    public static void main(String[] args) throws Exception {
        System.out.println("ProjectOwnerOperationScope: " + runAll() + " scenarios passed");
    }
    public static synchronized int runAll() throws Exception {
        cases = 0;
        for (boolean stage : new boolean[]{false, true}) {
            for (String owner : new String[]{"SOL", "OTHER_OWNER"}) {
                for (String action : new String[]{"CREATE", "SAVE", "COMPLETE", "COPY"}) {
                    scenario(owner + "/" + action + "/" + stage, () -> command(owner, action, stage));
                }
            }
            scenario("wrong declarations/" + stage, () -> wrongDeclarations(stage));
            scenario("target isolation/" + stage, () -> targetIsolation(stage));
            scenario("nested invocation/" + stage, () -> nested(stage));
            scenario("expired proofs/" + stage, () -> expired(stage));
        }
        scenario("standalone retains legacy path", () -> {
            var resolved = new AtomicBoolean();
            equal("result", ProjectOwnerOperationScope.call("SOL", "ENTITY", () -> {
                resolved.set(true); throw new AssertionError("must not resolve an execution");
            }, () -> "result"));
            check(!resolved.get());
            var request = ProjectOwnerOperationScope.writeRequest(1L, 2L, 3L, "SOL", "ENTITY", "40", null);
            check(request.ownerProof() == null && request.operationCode() == null);
            check(ProjectOwnerOperationScope.executionFor(1L, 2L, 3L, "SOL", "ENTITY", "40", null) == null);
        });
        scenario("verified frame alone cannot attest a callback", () -> {
            try (var v = ProjectVerifiedOperationScope.open(frame("SOL", "SAVE", "40", false))) {
                rejected(() -> ProjectOwnerOperationScope.writeRequest(1L, 2L, 3L, "SOL", "ENTITY", "40", selection(false)));
                check(!ProjectVerifiedOperationScope.matches(1L, 2L, 3L, "SOL", "ENTITY", selection(false)));
            }
        });
        scenario("exception closes declaration", () -> {
            var frame = frame("SOL", "SAVE", "40", false);
            try (var v = ProjectVerifiedOperationScope.open(frame)) {
                rejected(() -> ProjectOwnerOperationScope.call("SOL", "ENTITY", () -> declaration(frame), () -> {
                    throw new IllegalStateException("business failure");
                }));
                rejected(() -> ProjectOwnerOperationScope.writeRequest(1L, 2L, 3L, "SOL", "ENTITY", "40", selection(false)));
                try (var scope = ProjectOwnerOperationScope.open(declaration(frame))) { check(proof("40", false).ownerProof() != null); }
            }
        });
        scenario("cross thread and close order", () -> {
            var frame = frame("SOL", "COPY", "40", false);
            try (var v = ProjectVerifiedOperationScope.open(frame);
                 var s = ProjectOwnerOperationScope.open(declaration(frame))) {
                var request = proof("40", false);
                var failure = new AtomicReference<Throwable>();
                var thread = new Thread(() -> {
                    try { check(!request.ownerProof().matches(1L, 2L, request)); rejected(s::close); }
                    catch (Throwable ex) { failure.set(ex); }
                });
                thread.start(); thread.join();
                if (failure.get() != null) throw new AssertionError(failure.get());
                check(request.ownerProof().matches(1L, 2L, request));
            }
        });
        return cases;
    }
    private static void command(String owner, String action, boolean stage) {
        String source = "CREATE".equals(action) ? null : "40";
        var frame = frame(owner, action, source, stage);
        try (var v = ProjectVerifiedOperationScope.open(frame)) {
            ProjectOwnerOperationScope.call(owner, "ENTITY", () -> declaration(frame), () -> {
                equal(selection(stage), ProjectOwnerOperationScope.executionFor(1L, 2L, 3L, owner, "ENTITY", source, null));
                var request = ProjectOwnerOperationScope.writeRequest(1L, 2L, 3L, owner, "ENTITY", source, selection(stage));
                check(request.ownerProof().matches(1L, 2L, request)); equal(source, request.objectId());
                for (int callback = 0; callback < 4; callback++) check(request.ownerProof().matches(1L, 2L, request));
                if ("CREATE".equals(action) || "COPY".equals(action)) {
                    rejected(() -> ProjectOwnerOperationScope.writeRequest(1L, 2L, 3L, owner, "ENTITY", "41", selection(stage)));
                    ProjectOwnerOperationScope.registerCreated(1L, 2L, 3L, owner, "ENTITY", frame.operationCode(), 1, source, "41");
                    var derived = ProjectOwnerOperationScope.writeRequest(1L, 2L, 3L, owner, "ENTITY", "41", selection(stage));
                    equal("41", derived.objectId()); equal(source, ProjectVerifiedOperationScope.current().objectId());
                    check(derived.ownerProof().matches(1L, 2L, derived));
                    equal(selection(stage), ProjectOwnerOperationScope.executionFor(1L, 2L, 3L, owner, "ENTITY", "41", null));
                }
                return null;
            });
        }
    }
    private static void wrongDeclarations(boolean stage) {
        var f = frame("SOL", "SAVE", "40", stage);
        try (var v = ProjectVerifiedOperationScope.open(f)) {
            for (var d : new ProjectOwnerOperationScope.Declaration[]{
                    new ProjectOwnerOperationScope.Declaration(9L,2L,3L,"SOL","ENTITY",f.operationCode(),1,"40",selection(stage)),
                    new ProjectOwnerOperationScope.Declaration(1L,9L,3L,"SOL","ENTITY",f.operationCode(),1,"40",selection(stage)),
                    new ProjectOwnerOperationScope.Declaration(1L,2L,9L,"SOL","ENTITY",f.operationCode(),1,"40",selection(stage)),
                    new ProjectOwnerOperationScope.Declaration(1L,2L,3L,"SOL","ENTITY","SOL.ENTITY.COPY",1,"40",selection(stage)),
                    new ProjectOwnerOperationScope.Declaration(1L,2L,3L,"SOL","ENTITY",f.operationCode(),2,"40",selection(stage)),
                    new ProjectOwnerOperationScope.Declaration(1L,2L,3L,"SOL","ENTITY",f.operationCode(),1,"41",selection(stage)),
                    new ProjectOwnerOperationScope.Declaration(1L,2L,3L,"SOL","ENTITY",f.operationCode(),1,"40",selection(!stage))}) {
                rejected(() -> { try (var unused = ProjectOwnerOperationScope.open(d)) { throw new AssertionError("accepted"); } });
            }
        }
    }
    private static void targetIsolation(boolean stage) {
        var f = frame("SOL", "COPY", "40", stage);
        try (var v = ProjectVerifiedOperationScope.open(f); var s = ProjectOwnerOperationScope.open(declaration(f))) {
            var request = proof("40", stage);
            check(!request.ownerProof().matches(9L, 2L, request)); check(!request.ownerProof().matches(1L, 9L, request));
            for (var wrong : new WriteRequest[]{
                    new WriteRequest(3L,"SOL","ENTITY",selection(stage),f.operationCode(),1,"41",request.ownerProof()),
                    new WriteRequest(3L,"SOL","ENTITY",selection(stage),"SOL.ENTITY.SAVE",1,"40",request.ownerProof()),
                    new WriteRequest(3L,"SOL","ENTITY",selection(stage),f.operationCode(),2,"40",request.ownerProof()),
                    new WriteRequest(9L,"SOL","ENTITY",selection(stage),f.operationCode(),1,"40",request.ownerProof()),
                    new WriteRequest(3L,"OTHER","ENTITY",selection(stage),f.operationCode(),1,"40",request.ownerProof()),
                    new WriteRequest(3L,"SOL","OTHER",selection(stage),f.operationCode(),1,"40",request.ownerProof()),
                    new WriteRequest(3L,"SOL","ENTITY",selection(!stage),f.operationCode(),1,"40",request.ownerProof())}) {
                check(!wrong.ownerProof().matches(1L, 2L, wrong));
            }
            rejected(() -> ProjectOwnerOperationScope.executionFor(1L, 2L, 3L, "SOL", "ENTITY", "41", null));
            rejected(() -> ProjectOwnerOperationScope.executionFor(1L, 2L, 3L, "SOL", "ENTITY", "40", selection(!stage)));
            rejected(() -> ProjectOwnerOperationScope.registerCreated(1L, 2L, 3L, "SOL", "ENTITY", f.operationCode(), 1, "41", "42"));
            rejected(() -> ProjectOwnerOperationScope.registerCreated(1L, 2L, 3L, "SOL", "ENTITY", "SOL.ENTITY.SAVE", 1, "40", "42"));
        }
    }
    private static void nested(boolean stage) {
        var f = frame("SOL", "COPY", "40", stage);
        try (var v = ProjectVerifiedOperationScope.open(f); var s = ProjectOwnerOperationScope.open(declaration(f))) {
            var request = proof("40", stage);
            rejected(() -> ProjectOwnerOperationScope.call("SOL", "ENTITY", () -> declaration(f), () -> null));
            var child = frame("SOL", "SAVE", "50", stage);
            try (var cv = ProjectVerifiedOperationScope.open(child); var cs = ProjectOwnerOperationScope.open(declaration(child))) {
                check(!request.ownerProof().matches(1L, 2L, request));
                rejected(s::close);
                check(proof("50", stage).ownerProof() != null);
                rejected(() -> proof("40", stage));
            }
            check(request.ownerProof().matches(1L, 2L, request));
        }
    }
    private static void expired(boolean stage) {
        var f = frame("SOL", "SAVE", "40", stage);
        WriteRequest request;
        try (var v = ProjectVerifiedOperationScope.open(f); var s = ProjectOwnerOperationScope.open(declaration(f))) { request = proof("40", stage); }
        check(!request.ownerProof().matches(1L, 2L, request));
        try (var v = ProjectVerifiedOperationScope.open(f); var s = ProjectOwnerOperationScope.open(declaration(f))) {
            check(!request.ownerProof().matches(1L, 2L, request));
        }
    }
    private static WriteRequest proof(String target, boolean stage) {
        return ProjectOwnerOperationScope.writeRequest(1L,2L,3L,"SOL","ENTITY",target,selection(stage));
    }
    private static ProjectVerifiedOperationScope.Frame frame(String owner, String action, String objectId, boolean stage) {
        return new ProjectVerifiedOperationScope.Frame(1L,2L,3L,owner,"ENTITY",owner+".ENTITY."+action,1,objectId,selection(stage));
    }
    private static ProjectOwnerOperationScope.Declaration declaration(ProjectVerifiedOperationScope.Frame f) {
        // Test fixture only. Production declarations are formed by the actual Owner method and business row.
        return new ProjectOwnerOperationScope.Declaration(f.tenantId(),f.actorId(),f.projectId(),f.ownerContext(),f.objectType(),
                f.operationCode(),f.operationVersion(),f.objectId(),f.execution());
    }
    private static ProjectBusinessExecutionSelection selection(boolean stage) {
        return stage ? new ProjectBusinessExecutionSelection(null,new ProjectStageExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,true))
                : new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,8L,1,true,null),null);
    }
    @FunctionalInterface private interface Case { void run() throws Exception; }
    private static void scenario(String name, Case test) throws Exception {
        try { test.run(); cases++; } catch (Throwable failure) { throw new AssertionError(name, failure); }
    }
    private static void rejected(Runnable work) {
        try { work.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; }
        throw new AssertionError("rejection required");
    }
    private static void equal(Object expected, Object actual) { if (!Objects.equals(expected,actual)) throw new AssertionError(expected+" != "+actual); }
    private static void check(boolean value) { if (!value) throw new AssertionError("check failed"); }
}
