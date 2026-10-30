package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static cn.iocoder.yudao.module.pms.project.domain.template.operation.ProjectOperationPermissionIndex.Status;

/** Same cases are used by JDK execution and the JUnit bridge; these are not two independent test sets. */
public final class ProjectOperationPermissionCases {
    public static final class OwnerCommand {
        private static int calls;
        public void apply() { calls++; }
    }
    private ProjectOperationPermissionCases() { }
    public static List<String> runAll() {
        List<String> passed = new ArrayList<>();
        var create = descriptor("SURVEY.CREATE", 1, "SOL", "SURVEY");
        var update = descriptor("SURVEY.UPDATE", 1, "SOL", "SURVEY");
        var confirm = descriptor("SURVEY.CONFIRM", 1, "SOL", "SURVEY");
        var fixture = new ProjectOperationPermissionIndex(List.of(provider(List.of(create, update, confirm),
                Map.of(create.operationCode(), "survey:create", update.operationCode(), "survey:update", confirm.operationCode(), "survey:update"))));
        test(passed, "unique permission selects the real operation", () -> {
            var result = fixture.resolve("SOL", "SURVEY", "survey:create", null);
            equal(Status.RESOLVED, result.status()); equal(create, result.selected());
        });
        test(passed, "shared permission never chooses first operation", () -> {
            var result = fixture.resolve("SOL", "SURVEY", "survey:update", null);
            equal(Status.AMBIGUOUS_OPERATION, result.status()); equal(null, result.selected()); equal(2, result.candidates().size());
        });
        test(passed, "existing operation code disambiguates shared permission", () ->
                equal(confirm, fixture.resolve("SOL", "SURVEY", "survey:update", confirm.operationCode()).selected()));
        test(passed, "operation code cannot bypass a mismatched permission", () ->
                equal(Status.NOT_FOUND, fixture.resolve("SOL", "SURVEY", "survey:create", confirm.operationCode()).status()));
        test(passed, "unknown permission does not broaden lookup", () ->
                equal(Status.NOT_FOUND, fixture.resolve("SOL", "SURVEY", "unknown", null).status()));
        test(passed, "Owner mismatch is not a shared permission match", () ->
                equal(Status.NOT_FOUND, fixture.resolve("ACC", "SURVEY", "survey:create", null).status()));
        test(passed, "entity mismatch is not a shared permission match", () ->
                equal(Status.NOT_FOUND, fixture.resolve("SOL", "OTHER", "survey:create", null).status()));
        test(passed, "invalid required query fields are not wildcards", () -> {
            equal(Status.INVALID_REQUEST, fixture.resolve(null, "SURVEY", "survey:create", null).status());
            equal(Status.INVALID_REQUEST, fixture.resolve("SOL", " ", "survey:create", null).status());
            equal(Status.INVALID_REQUEST, fixture.resolve("SOL", "SURVEY", "", null).status());
            equal(Status.INVALID_REQUEST, fixture.resolve("SOL", "SURVEY", "survey:create", " ").status());
        });
        test(passed, "no trimming or permission prefix inference", () -> {
            equal(Status.NOT_FOUND, fixture.resolve("SOL", "SURVEY", " survey:create ", null).status());
            equal(Status.NOT_FOUND, fixture.resolve("SOL", "SURVEY", "survey:*", null).status());
        });
        test(passed, "no inferred permission for old providers", () -> {
            ProjectBusinessOperationProvider old = () -> List.of(create);
            var index = new ProjectOperationPermissionIndex(List.of(old));
            equal(create, index.find(create.operationCode(), 1)); equal(List.of(create), index.all());
            equal(null, index.permissionCode(create.operationCode(), 1));
            equal(Status.NOT_FOUND, index.resolve("SOL", "SURVEY", "CREATE", null).status());
        });
        test(passed, "multiple versions never use latest or default one", () -> {
            var v2 = descriptor(create.operationCode(), 2, "SOL", "SURVEY");
            var index = new ProjectOperationPermissionIndex(List.of(provider(List.of(v2, create), Map.of(create.operationCode(), "survey:create"))));
            equal(Status.AMBIGUOUS_VERSION, index.resolve("SOL", "SURVEY", "survey:create", null).status());
            equal(Status.AMBIGUOUS_VERSION, index.resolve("SOL", "SURVEY", "survey:create", create.operationCode()).status());
            equal(create, index.find(create.operationCode(), 1)); equal(v2, index.find(create.operationCode(), 2));
            equal(null, index.find(create.operationCode(), 3));
        });
        test(passed, "explicit metadata may omit older versions without losing exact lookup", () -> {
            var v2 = descriptor(create.operationCode(), 2, "SOL", "SURVEY");
            var index = new ProjectOperationPermissionIndex(List.of(() -> List.of(create), provider(List.of(v2), Map.of(v2.operationCode(), "survey:create"))));
            equal(v2, index.resolve("SOL", "SURVEY", "survey:create", null).selected());
            equal(create, index.find(create.operationCode(), 1));
        });
        test(passed, "candidate order is deterministic but not a selection policy", () -> {
            var reverse = new ProjectOperationPermissionIndex(List.of(provider(List.of(confirm, update, create),
                    Map.of(create.operationCode(), "survey:create", update.operationCode(), "survey:update", confirm.operationCode(), "survey:update"))));
            equal(fixture.resolve("SOL", "SURVEY", "survey:update", null), reverse.resolve("SOL", "SURVEY", "survey:update", null));
        });
        test(passed, "duplicate exact registrations retain startup rejection", () -> rejected("OPERATION_DESCRIPTOR_DUPLICATE", () ->
                new ProjectOperationPermissionIndex(List.of(() -> List.of(create), () -> List.of(create)))));
        test(passed, "a provider cannot attach permissions to another provider's operations", () -> rejected("OPERATION_PERMISSION_MAPPING_INVALID", () ->
                new ProjectOperationPermissionIndex(List.of(() -> List.of(create), provider(List.of(update), Map.of(create.operationCode(), "survey:create"))))));
        test(passed, "unknown mapping key is not ignored", () -> rejected("OPERATION_PERMISSION_MAPPING_INVALID", () ->
                new ProjectOperationPermissionIndex(List.of(provider(List.of(create), Map.of("TYPO", "survey:create"))))));
        test(passed, "blank mapping value is not a default permission", () -> rejected("OPERATION_PERMISSION_MAPPING_INVALID", () ->
                new ProjectOperationPermissionIndex(List.of(provider(List.of(create), Map.of(create.operationCode(), " "))))));
        test(passed, "explicit null permission is rejected rather than omitted", () -> {
            var map = new HashMap<String, String>(); map.put(create.operationCode(), null);
            rejected("OPERATION_PERMISSION_MAPPING_INVALID", () -> new ProjectOperationPermissionIndex(List.of(provider(List.of(create), map))));
        });
        test(passed, "null metadata collections fail explicitly", () -> {
            rejected("OPERATION_CATALOG_UNAVAILABLE", () -> new ProjectOperationPermissionIndex(null));
            rejected("OPERATION_PROVIDER_INVALID", () -> new ProjectOperationPermissionIndex(Arrays.asList((ProjectBusinessOperationProvider) null)));
            rejected("OPERATION_PROVIDER_INVALID", () -> new ProjectOperationPermissionIndex(List.of(provider(null, Map.of()))));
            rejected("OPERATION_PROVIDER_INVALID", () -> new ProjectOperationPermissionIndex(List.of(provider(List.of(create), null))));
            rejected("OPERATION_TARGET_NOT_DEPLOYED", () -> new ProjectOperationPermissionIndex(List.of(provider(Arrays.asList(create, null), Map.of()))));
        });
        test(passed, "external list and map mutations do not alter an installed index", () -> {
            var rows = new ArrayList<>(List.of(create)); var map = new HashMap<>(Map.of(create.operationCode(), "survey:create"));
            var providers = new ArrayList<>(List.of(provider(rows, map)));
            var index = new ProjectOperationPermissionIndex(providers);
            rows.clear(); map.clear(); providers.clear();
            equal(create, index.resolve("SOL", "SURVEY", "survey:create", null).selected());
        });
        test(passed, "results and metadata lists cannot be mutated", () -> {
            immutable(() -> fixture.all().clear());
            immutable(() -> fixture.resolve("SOL", "SURVEY", "survey:update", null).candidates().clear());
        });
        test(passed, "one index load reads provider metadata only once", () -> {
            var counter = new AtomicInteger();
            var p = new ProjectBusinessOperationProvider() {
                @Override public List<ProjectBusinessOperationDescriptor> operations() { counter.incrementAndGet(); return List.of(create); }
                @Override public Map<String, String> permissionCodes() { counter.incrementAndGet(); return Map.of(create.operationCode(), "survey:create"); }
            };
            var index = new ProjectOperationPermissionIndex(List.of(p));
            index.resolve("SOL", "SURVEY", "survey:create", null); index.all(); index.find(create.operationCode(), 1);
            equal(2, counter.get());
        });
        test(passed, "another Owner uses the same implementation without entity branches", () -> {
            var other = descriptor("WAREHOUSE.RECEIVE", 1, "WAREHOUSE", "RECEIPT");
            var index = new ProjectOperationPermissionIndex(List.of(provider(List.of(other), Map.of(other.operationCode(), "warehouse:receive"))));
            equal(other, index.resolve("WAREHOUSE", "RECEIPT", "warehouse:receive", null).selected());
        });
        test(passed, "catalog lookup never invokes a business command", () -> {
            fixture.resolve("SOL", "SURVEY", "survey:create", null);
            equal(0, OwnerCommand.calls);
        });
        test(passed, "empty registry is compatible and has no fallback", () -> {
            var index = new ProjectOperationPermissionIndex(List.of()); equal(List.of(), index.all());
            equal(Status.NOT_FOUND, index.resolve("SOL", "SURVEY", "survey:create", null).status());
        });
        return List.copyOf(passed);
    }
    public static void main(String[] args) {
        var result = runAll(); result.forEach(name -> System.out.println("PASS " + name));
        System.out.println("TOTAL " + result.size() + " passed; 0 failed");
    }
    private static ProjectBusinessOperationDescriptor descriptor(String code, int version, String owner, String type) {
        return new ProjectBusinessOperationDescriptor(code, version, owner, type, code, "ACTION", Set.of("PRE", "POST"), OwnerCommand.class, "apply");
    }
    private static ProjectBusinessOperationProvider provider(List<ProjectBusinessOperationDescriptor> descriptors, Map<String, String> permissions) {
        return new ProjectBusinessOperationProvider() {
            @Override public List<ProjectBusinessOperationDescriptor> operations() { return descriptors; }
            @Override public Map<String, String> permissionCodes() { return permissions; }
        };
    }
    private static void rejected(String message, Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { equal(message, expected.getMessage()); return; }
        throw new AssertionError("Expected rejection: " + message);
    }
    private static void immutable(Runnable action) {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Expected immutable collection");
    }
    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void test(List<String> passed, String name, Runnable action) {
        try { action.run(); passed.add(name); } catch (Throwable failure) { throw new AssertionError(name, failure); }
    }
}
