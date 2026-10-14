package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverRoleApprovalFilePolicyTest {
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void frozenRoleMemberReadsDownloadsAndPreviewsPlanWithoutProjectScope() {
        Fixture f = new Fixture();
        for (String action : List.of("READ", "DOWNLOAD", "PREVIEW")) {
            var fact = f.policy("CUTOVER_PLAN").inspect(query("CUTOVER_PLAN", "10", "FULL_PLAN", action));
            assertTrue(fact.allowed());
            assertEquals(20L, fact.scopeVersion());
            assertEquals("IMMUTABLE", fact.referenceMutability());
        }
        verifyNoInteractions(f.scopes);
        verifyNoInteractions(f.permissions);
    }
    @Test void frozenRoleReadWinsEvenWithProjectViewButWithoutOrdinaryPlanPermission() {
        Fixture f = new Fixture();
        when(f.scopes.inspect(8L, 30L, "ACTION_VIEW")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(30L, 9L, true));
        var fact = f.policy("CUTOVER_PLAN").inspect(query("CUTOVER_PLAN", "10", "FULL_PLAN", "READ"));
        assertTrue(fact.allowed());
        assertEquals(20L, fact.scopeVersion());
        verifyNoInteractions(f.scopes, f.permissions);
    }
    @Test void ordinaryProjectReadPreservesItsScopeVersionWithoutRoleAudience() {
        Fixture f = new Fixture();
        when(f.audience.inspectFile(any())).thenReturn(null);
        when(f.scopes.inspect(8L, 30L, "ACTION_VIEW")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(30L, 9L, true));
        when(f.permissions.hasAnyPermissions(8L, "pms:cutover-task:query", "pms:cutover-task:query-plan")).thenReturn(true);
        var fact = f.policy("CUTOVER_PLAN").inspect(query("CUTOVER_PLAN", "10", "FULL_PLAN", "READ"));
        assertTrue(fact.allowed());
        assertEquals(9L, fact.scopeVersion());
        verify(f.audience).inspectFile(any());
        verify(f.scopes, never()).lockAndRevalidate(any());
    }
    @Test void roleAudienceDoesNotGrantUploadReferenceReplaceOrDetach() {
        Fixture f = new Fixture();
        for (String action : List.of("UPLOAD", "REFERENCE", "REPLACE", "DETACH"))
            assertFalse(f.policy("CUTOVER_PLAN").inspect(query("CUTOVER_PLAN", "10", "FULL_PLAN", action)).allowed());
        verifyNoInteractions(f.audience);
        verify(f.scopes, times(4)).inspect(8L, 30L, "ACTION_EDIT");
    }
    @Test void closureRetainsItsOrdinaryProjectScopeAndCannotUseRoleAudience() {
        Fixture f = new Fixture();
        for (String action : List.of("READ", "DOWNLOAD", "PREVIEW"))
            assertFalse(f.policy("CUTOVER_CLOSURE").inspect(query("CUTOVER_CLOSURE", "40", "OTHER_EVIDENCE", action)).allowed());
        verifyNoInteractions(f.audience);
        verify(f.scopes, times(3)).inspect(8L, 30L, "ACTION_VIEW");
    }
    @Test void tenantOwnerAndPurposeChecksStillPrecedeRoleAudience() {
        Fixture f = new Fixture();
        assertThrows(SecurityException.class, () -> f.policy("CUTOVER_PLAN").inspect(
                new FileBusinessObjectPolicyQuery(2L, 8L, "CUT", "CUTOVER_PLAN", "10", "FULL_PLAN", "cutover-plan:50:2", "READ")));
        assertFalse(f.policy("CUTOVER_PLAN").inspect(
                new FileBusinessObjectPolicyQuery(1L, 8L, "IMP", "CUTOVER_PLAN", "10", "FULL_PLAN", "cutover-plan:50:2", "READ")).allowed());
        assertFalse(f.policy("CUTOVER_PLAN").inspect(query("CUTOVER_PLAN", "10", "OTHER_EVIDENCE", "READ")).allowed());
        verifyNoInteractions(f.audience);
    }
    private static FileBusinessObjectPolicyQuery query(String type, String object, String purpose, String action) {
        return new FileBusinessObjectPolicyQuery(1L, 8L, "CUT", type, object, purpose, "cutover-plan:50:2", action);
    }
    private static final class Fixture {
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
        final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
        final CutoverClosureMapper closures = mock(CutoverClosureMapper.class);
        final CutoverProjectScopePort scopes = mock(CutoverProjectScopePort.class);
        final PermissionApi permissions = mock(PermissionApi.class);
        final CutoverRuntimeRoleApprovalAudience audience = mock(CutoverRuntimeRoleApprovalAudience.class);
        Fixture() {
            CutoverTaskDO task = new CutoverTaskDO(); task.setId(10L); task.setTenantId(1L);
            task.setProjectId(30L); task.setOwnerUserId(7L); task.setCurrentStage("P4");
            when(tasks.selectById(10L)).thenReturn(task);
            when(scopes.inspect(any(), any(), any())).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(30L, 9L, false));
            var grant = new CutoverRuntimeRoleApprovalAudience.Grant(20L,
                    "{\"plan\":{\"planRevisionId\":50,\"planVersion\":2,\"content\":{\"editMode\":\"FULL_FILE_UPLOAD\",\"ownershipConfirmed\":true,\"fileArtifactFact\":{\"artifactId\":60,\"versionNo\":1,\"referenceKey\":\"cutover-plan:50:2\",\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":1,\"availabilityVersion\":1},\"scopeVersion\":30,\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}}},\"riskItems\":[],\"businessSurveyItems\":[]}");
            when(audience.inspectFile(any())).thenReturn(grant);
            CutoverClosureDO closure = new CutoverClosureDO(); closure.setId(40L); closure.setTenantId(1L);
            closure.setTaskId(10L); closure.setStatusCode("DRAFT");
            when(closures.selectById(40L)).thenReturn(closure);
        }
        CutoverFilePolicy policy(String type) {
            return new CutoverFilePolicy(type, tasks, checklists, items, closures, scopes, permissions, audience);
        }
    }
}
