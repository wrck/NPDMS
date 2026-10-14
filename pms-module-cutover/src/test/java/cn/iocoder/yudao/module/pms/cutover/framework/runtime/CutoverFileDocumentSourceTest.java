package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverFileDocumentSourceTest {
    private final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
    private final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
    private final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
    private final CutoverClosureMapper closures = mock(CutoverClosureMapper.class);
    private final CutoverProjectScopePort scopes = mock(CutoverProjectScopePort.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    private CutoverTaskDO task;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        task = new CutoverTaskDO();
        task.setId(1400L);
        task.setTenantId(1L);
        task.setProjectId(10L);
        task.setCurrentStage("P4");
        task.setOwnerUserId(8L);
        task.setVersion(2L);
        task.setTaskOrigin("NEW_PLATFORM");
        when(tasks.selectById(1400L)).thenReturn(task);
        for (String type : List.of("CUTOVER_PLAN", "CUTOVER_CHECKLIST_ITEM", "CUTOVER_CLOSURE")) {
            beanFactory.registerSingleton(type, new CutoverFilePolicy(
                    type, tasks, checklists, items, closures, scopes, permissions,
                    mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience.class)));
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void nativePlanRegistrationResolvesExactlyOneCutoverDocumentSource() {
        var sources = resolve(document("CUT", "CUTOVER_PLAN", "1400", "FULL_PLAN"));
        assertEquals(1, sources.size());
        var source = sources.getFirst();
        assertEquals(10L, source.projectId());
        assertEquals("CUT.PLAN", source.sourceCode());
        assertEquals("CUT", source.ownerModule());
        assertEquals("cutoverTask", source.entityType());
        assertEquals(1400L, source.entityId());
        assertNull(source.revisionId());
        assertTrue(source.hasBusinessOwner());
    }

    @Test
    void checklistEvidenceRetainsItsChecklistRevisionAndTaskRoot() {
        var item = new CutoverChecklistItemDO();
        item.setId(3100L); item.setTenantId(1L); item.setChecklistId(3000L);
        var checklist = new CutoverChecklistDO();
        checklist.setId(3000L); checklist.setTenantId(1L); checklist.setCutoverTaskId(1400L);
        when(items.selectById(3100L)).thenReturn(item);
        when(checklists.selectById(3000L)).thenReturn(checklist);
        assertScope(resolve(document("CUT", "CUTOVER_CHECKLIST_ITEM", "3100", "MANUAL_EVIDENCE")),
                "CUT.CHECKLIST_EVIDENCE", 3000L);
        verifyNoInteractions(scopes, permissions);
    }

    @Test
    void submittedClosurePurposesRetainHistoricalTaskAndClosureIdentity() {
        task.setTaskStatus("ARCHIVED");
        var closure = new CutoverClosureDO();
        closure.setId(4000L); closure.setTenantId(1L); closure.setTaskId(1400L);
        closure.setStatusCode("SUBMITTED");
        when(closures.selectById(4000L)).thenReturn(closure);
        var purposes = java.util.Map.of("POST_COLLECTION_CHECKLIST", "CUT.POST_COLLECTION_CHECKLIST",
                "IMPLEMENTATION_COMMITMENT", "CUT.IMPLEMENTATION_COMMITMENT",
                "OTHER_EVIDENCE", "CUT.CLOSURE_EVIDENCE",
                "MANUAL_COLLECTION_RESULT", "CUT.MANUAL_COLLECTION_RESULT");
        purposes.forEach((purpose, code) -> assertScope(
                resolve(document("CUT", "CUTOVER_CLOSURE", "4000", purpose)), code, 4000L));
        assertScope(resolve(document("CUT", "CUTOVER_PLAN", "1400", "FULL_PLAN")), "CUT.PLAN", null);
        verifyNoInteractions(scopes, permissions);
    }

    @Test
    void unsupportedOwnerPurposeAndTenantNeverReadBusinessRows() {
        assertTrue(resolve(document("IMP", "CUTOVER_PLAN", "1400", "FULL_PLAN")).isEmpty());
        assertTrue(resolve(document("CUT", "CUTOVER_PLAN", "1400", "OTHER_EVIDENCE")).isEmpty());
        assertTrue(resolve(document("CUT", "UNKNOWN", "1400", "FULL_PLAN")).isEmpty());
        assertTrue(beanFactory.getBeanProvider(FileDocumentSourceProvider.class).orderedStream()
                .map(provider -> provider.resolve(2L, document("CUT", "CUTOVER_PLAN", "1400", "FULL_PLAN")))
                .allMatch(Objects::isNull));
        verifyNoInteractions(tasks, checklists, items, closures, scopes, permissions);
    }

    @Test
    void absentOrForeignRowsCannotResolveAnotherTenantsTask() {
        assertTrue(resolve(document("CUT", "CUTOVER_PLAN", "9999", "FULL_PLAN")).isEmpty());
        task.setTenantId(2L);
        assertTrue(resolve(document("CUT", "CUTOVER_PLAN", "1400", "FULL_PLAN")).isEmpty());
        var item = new CutoverChecklistItemDO();
        item.setId(3100L); item.setTenantId(2L); item.setChecklistId(3000L);
        when(items.selectById(3100L)).thenReturn(item);
        assertTrue(resolve(document("CUT", "CUTOVER_CHECKLIST_ITEM", "3100", "MANUAL_EVIDENCE")).isEmpty());
        var closure = new CutoverClosureDO();
        closure.setId(4000L); closure.setTenantId(2L); closure.setTaskId(1400L);
        when(closures.selectById(4000L)).thenReturn(closure);
        assertTrue(resolve(document("CUT", "CUTOVER_CLOSURE", "4000", "OTHER_EVIDENCE")).isEmpty());
        verifyNoInteractions(checklists, scopes, permissions);
    }

    private List<FileDocumentSourceProvider.Scope> resolve(FileEvidenceApi.Document document) {
        return beanFactory.getBeanProvider(FileDocumentSourceProvider.class).orderedStream()
                .map(provider -> provider.resolve(1L, document)).filter(Objects::nonNull).toList();
    }

    private static FileEvidenceApi.Document document(String owner, String type, String id, String purpose) {
        return new FileEvidenceApi.Document(1L, owner, type, id, purpose, "fixture", 2L, 1,
                "a".repeat(64), "fixture.pdf", true);
    }

    private static void assertScope(List<FileDocumentSourceProvider.Scope> sources, String code, Long revision) {
        assertEquals(1, sources.size());
        var source = sources.getFirst();
        assertEquals(10L, source.projectId());
        assertEquals(code, source.sourceCode());
        assertEquals("CUT", source.ownerModule());
        assertEquals("cutoverTask", source.entityType());
        assertEquals(1400L, source.entityId());
        assertEquals(revision, source.revisionId());
        assertTrue(source.hasBusinessOwner());
    }
}
