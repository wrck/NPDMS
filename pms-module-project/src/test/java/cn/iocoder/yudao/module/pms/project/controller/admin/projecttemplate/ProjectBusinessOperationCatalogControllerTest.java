package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Actual catalog/controller/JSON mapping; not HTTP security or application startup integration. */
class ProjectBusinessOperationCatalogControllerTest {
    public static class Owner { public void apply() { throw new AssertionError("metadata query invoked Owner"); } }
    private ProjectBusinessOperationDescriptor operation(String code, int version) {
        return new ProjectBusinessOperationDescriptor(code, version, "SOL", "OBJECT", code, "UPDATE", Set.of("PRE"), Owner.class, "apply");
    }
    private ProjectBusinessOperationProvider provider(List<ProjectBusinessOperationDescriptor> operations, Map<String, String> permissions) {
        return new ProjectBusinessOperationProvider() {
            @Override public List<ProjectBusinessOperationDescriptor> operations() { return operations; }
            @Override public Map<String, String> permissionCodes() { return permissions; }
        };
    }
    @Test void oldProviderStillListsWithoutInventingPermission() {
        var op = operation("OLD", 1);
        var registry = new ProjectBusinessOperationRegistry(List.of(() -> List.of(op)), List.of());
        var controller = new ProjectBusinessOperationCatalogController(registry);
        var listed = controller.list("SOL", "OBJECT").getData();
        assertEquals(1, listed.size()); assertNull(listed.getFirst().permissionCode());
        assertFalse(listed.getFirst().runtimeAvailable()); assertEquals(op, registry.find("OLD", 1));
        assertEquals("NOT_FOUND", controller.resolve("SOL", "OBJECT", "UPDATE", null).getData().status());
    }
    @Test void nativePermissionIsExposedWithoutJavaTargetsAndDoesNotAssertRuntimeReadiness() {
        var op = operation("SAVE", 1);
        var registry = new ProjectBusinessOperationRegistry(List.of(provider(List.of(op), Map.of("SAVE", "native:write"))), List.of());
        var controller = new ProjectBusinessOperationCatalogController(registry);
        var response = controller.resolve("SOL", "OBJECT", "native:write", null).getData();
        assertEquals("RESOLVED", response.status()); assertEquals("SAVE", response.selected().operationCode());
        assertFalse(response.selected().runtimeAvailable()); assertEquals("native:write", response.selected().permissionCode());
        var json = JsonUtils.parseTree(JsonUtils.toJsonString(response));
        assertFalse(json.path("selected").has("applicationService")); assertFalse(json.path("selected").has("methodName"));
    }
    @Test void sharedPermissionAndMultipleVersionsAreNeverAutoSelected() {
        var registry = new ProjectBusinessOperationRegistry(List.of(provider(List.of(operation("SAVE", 1), operation("SAVE", 2), operation("CONFIRM", 1)),
                Map.of("SAVE", "native:write", "CONFIRM", "native:write"))), List.of((code, version) -> version == 2));
        var controller = new ProjectBusinessOperationCatalogController(registry);
        var shared = controller.resolve("SOL", "OBJECT", "native:write", null).getData();
        assertEquals("AMBIGUOUS_OPERATION", shared.status()); assertNull(shared.selected());
        var versions = controller.resolve("SOL", "OBJECT", "native:write", "SAVE").getData();
        assertEquals("AMBIGUOUS_VERSION", versions.status()); assertNull(versions.selected());
        assertEquals("RESOLVED", controller.resolve("SOL", "OBJECT", "native:write", "CONFIRM").getData().status());
    }
    @Test void exactRegistryReadinessStillRequiresExactlyOneRuntimeHandler() {
        var op = operation("SAVE", 1);
        var providers = List.of(provider(List.of(op), Map.of("SAVE", "native:write")));
        assertTrue(new ProjectBusinessOperationRegistry(providers, List.of((code, version) -> true)).runtimeAvailable("SAVE", 1));
        assertFalse(new ProjectBusinessOperationRegistry(providers, List.of((code, version) -> true, (code, version) -> true)).runtimeAvailable("SAVE", 1));
    }
    @Test void permissionResolutionDoesNotCallRuntimeChecksOrBusinessHandlers() {
        var count = new AtomicInteger();
        var registry = new ProjectBusinessOperationRegistry(List.of(provider(List.of(operation("SAVE", 1)), Map.of("SAVE", "native:write"))),
                List.of((code, version) -> { count.incrementAndGet(); return true; }));
        assertNotNull(registry.resolvePermission("SOL", "OBJECT", "native:write", null).selected());
        assertEquals(0, count.get());
    }
    @Test void nonexistentNativeMethodStillRejectsCatalogConstruction() {
        var invalid = new ProjectBusinessOperationDescriptor("BAD", 1, "SOL", "OBJECT", "bad", "UPDATE", Set.of(), Owner.class, "notPresent");
        assertThrows(IllegalStateException.class, () -> new ProjectBusinessOperationRegistry(List.of(() -> List.of(invalid)), List.of()));
    }
    @Test void bothReadEndpointsRetainTheSameConfigurationPermissionBoundary() throws Exception {
        var list = ProjectBusinessOperationCatalogController.class.getMethod("list", String.class, String.class).getAnnotation(PreAuthorize.class);
        var resolve = ProjectBusinessOperationCatalogController.class.getMethod("resolve", String.class, String.class, String.class, String.class).getAnnotation(PreAuthorize.class);
        assertNotNull(resolve); assertEquals(list.value(), resolve.value());
        assertEquals("@ss.hasPermission('pms:project-template:update') or @ss.hasPermission('pms:project-plan:manage')", resolve.value());
    }
}
