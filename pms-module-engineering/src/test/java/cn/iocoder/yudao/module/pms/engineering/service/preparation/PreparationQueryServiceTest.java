package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemWaiverDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSourceReferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemWaiverMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationReadinessSnapshotMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationSourceReferenceMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_WORK_BINDING_NOT_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreparationQueryServiceTest {

    @Mock private PreparationMapper preparationMapper;
    @Mock private PreparationItemMapper itemMapper;
    @Mock private PreparationItemWaiverMapper waiverMapper;
    @Mock private DynamicFormInstanceMapper formMapper;
    @Mock private PreparationSourceReferenceMapper sourceMapper;
    @Mock private PreparationReadinessSnapshotMapper snapshotMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private ProjectWorkBindingFactApi workBindingFactApi;
    @Mock private ProjectParticipantFactApi participantFactApi;

    @InjectMocks
    private PreparationQueryService service;

    private final PreparationQueryService.Actor actor = new PreparationQueryService.Actor(1L, 7L);

    @BeforeEach
    void authorizeQuery() {
        org.mockito.Mockito.lenient().when(permissionApi.hasAnyPermissions(7L, PreparationQueryService.PERMISSION_QUERY,
                PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 3L, Set.of(100L), Set.of()));
    }

    @Test
    void itemProjectionUsesStableCursorAndBatchedForms() {
        when(permissionApi.hasAnyPermissions(7L, PreparationItemApplicationService.PERMISSION_FILL)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(participantFactApi.inspect(any())).thenReturn(new ProjectParticipantFact(
                100L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), null,
                "ACTIVE", "S1", 4, 9L));
        PreparationDO preparation = preparation();
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        PreparationItemDO first = item(200L, "POWER", 1);
        first.setWaiverPolicySnapshot("{\"allowed\":true}");
        PreparationItemDO second = item(201L, "FIBER", 2);
        when(itemMapper.selectPage(any())).thenReturn(List.of(first, second));
        DynamicFormInstanceDO form = new DynamicFormInstanceDO();
        form.setId(300L);
        form.setItemId(200L);
        form.setFormCode("POWER");
        form.setFormVersion(1);
        form.setSchemaSnapshot("{\"schemaVersion\":1}");
        form.setValueSnapshot("{}");
        form.setStatusCode("DRAFT");
        form.setVersion(0);
        when(formMapper.selectListByItemIds(any())).thenReturn(List.of(form));
        PreparationSourceReferenceDO source = new PreparationSourceReferenceDO();
        source.setId(400L);
        source.setItemId(200L);
        source.setSourceTypeCode("OA");
        source.setSourceObjectType("MATERIAL_REQUEST");
        source.setSourceObjectId("OA-1");
        source.setSourceReferenceKey("oa-1");
        source.setNormalizedResultCode("UNKNOWN");
        source.setSyncStatusCode("FAILED");
        source.setLastSuccessResultCode("ARRIVED");
        source.setLastSyncErrorCode("SOURCE_TIMEOUT");
        source.setVersion(2);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));
        when(waiverMapper.selectBusinessList(any())).thenReturn(List.of());
        PreparationPageReqVO request = new PreparationPageReqVO();
        request.setPageSize(1);

        var result = service.getItems(1000L, request, actor);

        assertEquals(1, result.items().size());
        assertTrue(result.hasMore());
        assertEquals("1|POWER|200", result.nextCursor());
        assertEquals(300L, result.items().getFirst().getForm().getFormInstanceId());
        assertEquals(1, result.items().getFirst().getSources().size());
        assertEquals("FAILED", result.items().getFirst().getSources().getFirst().getSyncStatusCode());
        assertEquals("ARRIVED", result.items().getFirst().getSources().getFirst().getLastSuccessResultCode());
        assertEquals(2, result.items().getFirst().getSources().getFirst().getSourceVersion());
        assertTrue(result.items().getFirst().getAllowedActions().contains("PATCH_ITEM"));
        assertTrue(result.items().getFirst().getAllowedActions().contains("REFRESH_SOURCE"));
        assertTrue(result.items().getFirst().getAllowedActions().contains("CREATE_WAIVER"));

        first.setSourcePolicySnapshot("{\"requirementCode\":\"NONE\"}");
        assertTrue(service.getItems(1000L, request, actor).items().getFirst().getAllowedActions().stream()
                .noneMatch("REFRESH_SOURCE"::equals));

        PreparationItemWaiverDO pending = new PreparationItemWaiverDO();
        pending.setItemCode("POWER");
        pending.setStatusCode("PENDING_APPROVAL");
        when(waiverMapper.selectBusinessList(any())).thenReturn(List.of(pending));
        assertTrue(service.getItems(1000L, request, actor).items().getFirst().getAllowedActions().stream()
                .noneMatch("CREATE_WAIVER"::equals));
    }

    @Test
    void currentManagerReceivesPreparationActionsButUnavailableFactFailsClosed() {
        when(permissionApi.hasAnyPermissions(any(), any(String[].class))).thenReturn(true);
        when(participantFactApi.inspect(any())).thenReturn(new ProjectParticipantFact(
                100L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), null,
                "ACTIVE", "S1", 4, 9L));
        PreparationDO preparation = preparation();
        when(preparationMapper.selectCurrent(any())).thenReturn(preparation);

        assertEquals(List.of("SUBMIT"), service.getCurrent(100L, "PRE_02", actor).getAllowedActions());

        when(participantFactApi.inspect(any())).thenThrow(new IllegalStateException("participant unavailable"));
        assertTrue(service.getCurrent(100L, "PRE_02", actor).getAllowedActions().isEmpty());
    }

    @Test
    void currentAssigneeReceivesOnlyAssigneePatchActions() {
        when(permissionApi.hasAnyPermissions(7L, PreparationItemApplicationService.PERMISSION_FILL)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(false);
        when(preparationMapper.selectById(any())).thenReturn(preparation());
        PreparationItemDO item = item(200L, "POWER", 1);
        item.setAssigneeUserId(7L);
        when(itemMapper.selectPage(any())).thenReturn(List.of(item));
        DynamicFormInstanceDO form = new DynamicFormInstanceDO();
        form.setId(300L);
        form.setItemId(200L);
        form.setFormCode("POWER");
        form.setFormVersion(1);
        form.setSchemaSnapshot("{\"schemaVersion\":1}");
        form.setValueSnapshot("{}");
        form.setStatusCode("DRAFT");
        form.setVersion(0);
        when(formMapper.selectListByItemIds(any())).thenReturn(List.of(form));
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(waiverMapper.selectBusinessList(any())).thenReturn(List.of());

        var actions = service.getItems(1000L, new PreparationPageReqVO(), actor)
                .items().getFirst().getAllowedActions();

        assertEquals(List.of("PATCH_ASSIGNEE_FIELDS", "PATCH_ITEM"), actions);
    }

    @Test
    void managerReceivesExactConfirmAndReturnActionsAndProjectedReason() {
        when(permissionApi.hasAnyPermissions(7L, PreparationItemApplicationService.PERMISSION_FILL)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(participantFactApi.inspect(any())).thenReturn(new ProjectParticipantFact(
                100L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), null,
                "ACTIVE", "S1", 4, 9L));
        PreparationDO preparation = preparation();
        preparation.setStatusCode("PENDING_CONFIRMATION");
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        PreparationItemDO item = item(200L, "POWER", 1);
        item.setApplicabilityCode("NOT_APPLICABLE_PENDING");
        item.setNotApplicableReason("现场条件不满足");
        when(itemMapper.selectPage(any())).thenReturn(List.of(item));
        DynamicFormInstanceDO form = form(200L, "FROZEN");
        when(formMapper.selectListByItemIds(any())).thenReturn(List.of(form));
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(waiverMapper.selectBusinessList(any())).thenReturn(List.of());

        var projected = service.getItems(1000L, new PreparationPageReqVO(), actor).items().getFirst();

        assertEquals("现场条件不满足", projected.getNotApplicableReason());
        assertTrue(projected.getAllowedActions().contains("CONFIRM_NOT_APPLICABLE_ITEM"));
        assertTrue(projected.getAllowedActions().contains("RETURN_ITEM"));
        assertTrue(projected.getAllowedActions().stream().noneMatch("REVIEW_ITEM"::equals));

        preparation.setStatusCode("CONFIRMED");
        item.setApplicabilityCode("REQUIRED");
        item.setConfirmationStatusCode("CONFIRMED");
        assertEquals(List.of("RETURN_ITEM", "REFRESH_SOURCE"),
                service.getItems(1000L, new PreparationPageReqVO(), actor)
                        .items().getFirst().getAllowedActions());
    }

    @Test
    void missingPreparationReturnsEmptyOnlyWhenWorkBindingExists() {
        when(preparationMapper.selectCurrent(any())).thenReturn(null);
        when(workBindingFactApi.inspect(any())).thenReturn(null);

        assertNull(service.getCurrent(100L, "PRE_02", actor));

        when(workBindingFactApi.inspect(any())).thenThrow(new IllegalStateException("missing binding"));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getCurrent(100L, "PRE_02", actor));
        assertEquals(PREPARATION_WORK_BINDING_NOT_AVAILABLE.getCode(), error.getCode());
    }

    @Test
    void readinessSnapshotsUseStableCursor() {
        when(preparationMapper.selectById(any())).thenReturn(preparation());
        PreparationReadinessSnapshotDO first = new PreparationReadinessSnapshotDO();
        first.setId(901L);
        first.setSnapshotNo(3);
        first.setResultCode("READY");
        PreparationReadinessSnapshotDO second = new PreparationReadinessSnapshotDO();
        second.setId(902L);
        second.setSnapshotNo(4);
        second.setResultCode("NOT_READY");
        when(snapshotMapper.selectPage(any())).thenReturn(List.of(first, second));
        PreparationPageReqVO request = new PreparationPageReqVO();
        request.setPageSize(1);

        var result = service.getReadinessSnapshots(1000L, request, actor);

        assertEquals(1, result.items().size());
        assertEquals(901L, result.items().getFirst().getSnapshotId());
        assertEquals("3:901", result.nextCursor());
        assertTrue(result.hasMore());
    }

    private PreparationDO preparation() {
        PreparationDO row = new PreparationDO();
        row.setId(1000L);
        row.setProjectId(100L);
        row.setPreparationTypeCode(PreparationInitializationService.PREPARATION_TYPE);
        row.setBusinessVersion(1);
        row.setCurrentMarker(1);
        row.setStatusCode("DRAFT");
        row.setReadinessStatusCode("NOT_READY");
        row.setVersion(0);
        return row;
    }

    private PreparationItemDO item(Long id, String code, Integer sortOrder) {
        PreparationItemDO row = new PreparationItemDO();
        row.setId(id);
        row.setPreparationId(1000L);
        row.setItemCode(code);
        row.setItemName(code);
        row.setSortOrder(sortOrder);
        row.setApplicabilityCode("REQUIRED");
        row.setConfirmationStatusCode("PENDING");
        row.setOutsourced(false);
        row.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        row.setWaiverPolicySnapshot("{\"allowed\":false}");
        row.setVersion(0);
        return row;
    }

    private DynamicFormInstanceDO form(Long itemId, String status) {
        DynamicFormInstanceDO row = new DynamicFormInstanceDO();
        row.setId(300L);
        row.setItemId(itemId);
        row.setFormCode("POWER");
        row.setFormVersion(1);
        row.setSchemaSnapshot("{\"schemaVersion\":1}");
        row.setValueSnapshot("{}");
        row.setStatusCode(status);
        row.setVersion(0);
        return row;
    }
}
