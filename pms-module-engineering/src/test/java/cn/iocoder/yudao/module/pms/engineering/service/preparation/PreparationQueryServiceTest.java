package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationReadinessSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
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
    @Mock private DynamicFormInstanceMapper formMapper;
    @Mock private PreparationReadinessSnapshotMapper snapshotMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private ProjectWorkBindingFactApi workBindingFactApi;

    @InjectMocks
    private PreparationQueryService service;

    private final PreparationQueryService.Actor actor = new PreparationQueryService.Actor(1L, 7L);

    @BeforeEach
    void authorizeQuery() {
        when(permissionApi.hasAnyPermissions(7L, PreparationQueryService.PERMISSION_QUERY,
                PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 3L, Set.of(100L), Set.of()));
    }

    @Test
    void itemProjectionUsesStableCursorAndBatchedForms() {
        PreparationDO preparation = preparation();
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        PreparationItemDO first = item(200L, "POWER", 1);
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
        PreparationPageReqVO request = new PreparationPageReqVO();
        request.setPageSize(1);

        var result = service.getItems(1000L, request, actor);

        assertEquals(1, result.items().size());
        assertTrue(result.hasMore());
        assertEquals("1|POWER|200", result.nextCursor());
        assertEquals(300L, result.items().getFirst().getForm().getFormInstanceId());
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
        row.setVersion(0);
        return row;
    }
}
