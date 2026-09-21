package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.Set;
import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableFilePolicyProvider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectDeliverableFilePolicyProviderTest {
    final AccProjectDeliverableMapper deliverables = mock(AccProjectDeliverableMapper.class);
    final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final FileBusinessObjectPolicyProvider provider = new ProjectDeliverableFilePolicyProvider(
            deliverables, rules, new ProjectDeliverableAccess(scopes, permissions));
    final FileReferenceSetKey key = new FileReferenceSetKey(OWNER, TYPE, "20", PURPOSE);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        var row = new AccProjectDeliverableDO(); row.setId(20L); row.setTenantId(7L);
        row.setProjectId(9L); row.setDeliverableCode("S1_D3");
        when(deliverables.selectById(20L)).thenReturn(row);
        when(deliverables.selectByIdForUpdate(any())).thenReturn(row);
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        var scope = new ProjectScopeResult(9L, 2L, Set.of(9L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope);
        when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        context("ACTIVE", "UPLOAD");
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    void context(String status, String source) {
        var context = new ProjectDeliverableRuleApi.Context(9L, 10L, status, 11L, "S1", null,
                JsonUtils.parseTree("{\"allowedSources\":[\"" + source + "\"]}"));
        when(rules.read(9L, "S1_D3")).thenReturn(context);
        when(rules.lock(9L, "S1_D3")).thenReturn(context);
    }

    @Test void uploadInitializationAndCompletionUseTheSameLockedProjectScopeWithoutPermittingReplacement() {
        var inspected = provider.inspect(new FileBusinessObjectPolicyQuery(7L, 11L, OWNER, TYPE, "20", PURPOSE,
                "file-slot", "UPLOAD"));
        var set = provider.lockAndRevalidateReferenceSet(new FileBusinessObjectReferenceSetRevalidationQuery(
                7L, 11L, key, "UPLOAD", inspected.scopeVersion()));
        var slot = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(7L, 11L, OWNER,
                TYPE, "20", PURPOSE, "file-slot", "UPLOAD", inspected.scopeVersion()));
        assertTrue(inspected.allowed()); assertEquals(inspected, set); assertEquals(set, slot);
        assertEquals("IMMUTABLE", set.referenceMutability());
        assertTrue(set.allowedMediaTypes().contains("application/pdf"));
        assertFalse(set.allowedMediaTypes().contains("text/plain"));
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "REPLACE")).allowed());
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "DETACH")).allowed());
    }

    @Test void referenceSetCompletionUsesManageScopeAndRejectsStaleScopeAndForeignOwnerKeys() {
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidateReferenceSet(
                new FileBusinessObjectReferenceSetRevalidationQuery(7L, 11L, key, "UPLOAD", 1L)));
        assertTrue(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 12L, key, "UPLOAD")).allowed());
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(8L, 11L, key, "UPLOAD")).allowed());
        var foreign = new FileReferenceSetKey("SOL", TYPE, "20", PURPOSE);
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, foreign, "UPLOAD")).allowed());
    }

    @Test void sourceConfigurationAndClosedProjectsBlockNewUploadsButPreserveHistoryReads() {
        context("ACTIVE", "BUSINESS_RESULT");
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "UPLOAD")).allowed());
        context("NORMAL_CLOSED", "UPLOAD");
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidateReferenceSet(
                new FileBusinessObjectReferenceSetRevalidationQuery(7L, 11L, key, "UPLOAD", 2L)));
        assertTrue(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "READ")).allowed());
    }
}
