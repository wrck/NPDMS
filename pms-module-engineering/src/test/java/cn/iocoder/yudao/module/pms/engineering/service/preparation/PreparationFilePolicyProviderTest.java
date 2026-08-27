package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemRowQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreparationFilePolicyProviderTest {

    @Mock private PreparationMapper preparationMapper;
    @Mock private PreparationItemMapper itemMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @InjectMocks private PreparationFilePolicyProvider provider;

    @Test
    void assigneeCanUploadMultipleEvidenceSlots() {
        stubLocated("DRAFT", 100L);
        when(permissionApi.hasAnyPermissions(100L, PreparationItemApplicationService.PERMISSION_FILL))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        var fact = provider.inspect(query(100L, FileActionCodes.UPLOAD));

        assertTrue(fact.allowed());
        assertEquals("MULTIPLE", fact.cardinality());
        assertEquals(Set.of("SITE_SURVEY_EVIDENCE"), fact.allowedCategoryCodes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPLOAD", "REPLACE", "REFERENCE", "DETACH"})
    void assigneeCanUseEveryApprovedMutatingAction(String action) {
        stubLocated("DRAFT", 100L);
        when(permissionApi.hasAnyPermissions(100L, PreparationItemApplicationService.PERMISSION_FILL))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertTrue(provider.inspect(query(100L, action)).allowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ", "DOWNLOAD", "PREVIEW"})
    void visibleProjectMemberCanUseEveryApprovedReadAction(String action) {
        stubLocated("DRAFT", 100L);
        when(permissionApi.hasAnyPermissions(100L, PreparationQueryService.PERMISSION_QUERY,
                PreparationInitializationService.PERMISSION_MANAGE,
                PreparationItemApplicationService.PERMISSION_FILL)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertTrue(provider.inspect(query(100L, action)).allowed());
    }

    @Test
    void archiveIsOutsideTheApprovedActionMatrix() {
        stubLocated("DRAFT", 100L);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertFalse(provider.inspect(query(100L, FileActionCodes.ARCHIVE)).allowed());
    }

    @Test
    void lockAndRevalidateUsesCurrentAssigneeAndScopeVersion() {
        stubLocated("DRAFT", 100L);
        when(permissionApi.hasAnyPermissions(100L, PreparationItemApplicationService.PERMISSION_FILL))
                .thenReturn(true);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation("DRAFT"));
        when(itemMapper.selectForUpdate(any())).thenReturn(item(100L));

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 100L, "SOL", "SITE_SURVEY_ITEM", "2", "SITE_SURVEY_EVIDENCE",
                "photo-1", FileActionCodes.REFERENCE, 9L));

        assertTrue(fact.allowed());
        assertEquals(9L, fact.scopeVersion());
    }

    @Test
    void copiedItemKeepsTheOriginalEvidenceSlotButUsesTheCurrentDraftAuthorization() {
        PreparationItemDO original = item(1L);
        PreparationItemDO current = item(20L, 100L);
        current.setSourceItemId(original.getId());
        when(itemMapper.selectByObjectId(any())).thenReturn(original);
        when(itemMapper.selectCurrentByEvidenceObjectId(any())).thenReturn(current);
        when(preparationMapper.selectById(any())).thenReturn(preparation(20L, "DRAFT"));
        when(permissionApi.hasAnyPermissions(100L, PreparationItemApplicationService.PERMISSION_FILL))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertTrue(provider.inspect(new FileBusinessObjectPolicyQuery(0L, 100L, "SOL",
                "SITE_SURVEY_ITEM", "2", "SITE_SURVEY_EVIDENCE", "site-survey-POWER",
                FileActionCodes.REPLACE)).allowed());
    }

    @Test
    void copiedItemLocksTheCurrentLineageRowWhenRevalidatingTheOriginalEvidenceSlot() {
        PreparationItemDO original = item(1L);
        PreparationItemDO current = item(20L, 100L);
        current.setSourceItemId(original.getId());
        when(itemMapper.selectByObjectId(any())).thenReturn(original);
        when(itemMapper.selectCurrentByEvidenceObjectId(any())).thenReturn(current);
        when(preparationMapper.selectById(any())).thenReturn(preparation(20L, "DRAFT"));
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation(20L, "DRAFT"));
        when(itemMapper.selectForUpdate(any())).thenReturn(current);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(permissionApi.hasAnyPermissions(100L, PreparationItemApplicationService.PERMISSION_FILL))
                .thenReturn(true);

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 100L, "SOL", "SITE_SURVEY_ITEM", String.valueOf(original.getId()),
                "SITE_SURVEY_EVIDENCE", "site-survey-POWER", FileActionCodes.REPLACE, 9L));

        assertTrue(fact.allowed());
        verify(itemMapper).selectForUpdate(new PreparationItemRowQuery(0L, 20L, current.getId()));
    }

    private void stubLocated(String status, Long assigneeId) {
        when(itemMapper.selectByObjectId(any())).thenReturn(item(assigneeId));
        when(preparationMapper.selectById(any())).thenReturn(preparation(status));
    }

    private PreparationDO preparation(String status) {
        return preparation(1L, status);
    }

    private PreparationDO preparation(Long id, String status) {
        PreparationDO row = new PreparationDO();
        row.setId(id); row.setProjectId(10L); row.setStatusCode(status);
        return row;
    }

    private PreparationItemDO item(Long assigneeId) {
        return item(1L, assigneeId);
    }

    private PreparationItemDO item(Long preparationId, Long assigneeId) {
        PreparationItemDO row = new PreparationItemDO();
        row.setId(preparationId.equals(1L) ? 2L : 96L); row.setPreparationId(preparationId);
        row.setApplicabilityCode("REQUIRED");
        row.setAssigneeUserId(assigneeId);
        return row;
    }

    private ProjectScopeResult scope() { return new ProjectScopeResult(10L, 9L, Set.of(10L), Set.of()); }
    private FileBusinessObjectPolicyQuery query(Long actorId, String action) {
        return new FileBusinessObjectPolicyQuery(0L, actorId, "SOL", "SITE_SURVEY_ITEM", "2",
                "SITE_SURVEY_EVIDENCE", "photo-1", action);
    }
}
