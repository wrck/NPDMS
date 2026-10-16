package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CutoverCollectionSourcesTest {
    private final CutoverTaskMapper tasks=mock(CutoverTaskMapper.class);
    private final CutoverFrozenDeviceMapper devices=mock(CutoverFrozenDeviceMapper.class);
    private final CutoverChecklistMapper checklists=mock(CutoverChecklistMapper.class);
    private final CutoverChecklistItemMapper items=mock(CutoverChecklistItemMapper.class);
    private final CutoverProjectScopePort scopes=mock(CutoverProjectScopePort.class);
    private final PermissionApi permissions=mock(PermissionApi.class);
    private final CutoverTaskDO task=new CutoverTaskDO();
    private CollectionSourceAdapter source;

    @BeforeEach void setup(){
        source=new CutoverCollectionSources(tasks,devices,checklists,items,mock(CutoverClosureMapper.class),scopes,permissions,mock(CutoverExecutionReadinessPolicy.class)).cutoverChecklistCollectionSource();
        task.setId(10L);task.setTenantId(1L);task.setProjectId(21L);task.setOwnerUserId(7L);
        task.setTaskOrigin("NEW_PLATFORM");task.setCurrentStage("P3");task.setTaskStatus("SURVEYING");
        var list=new CutoverChecklistDO();list.setId(20L);list.setTenantId(1L);list.setCutoverTaskId(10L);list.setStatusCode("DRAFT");list.setVersion(3L);
        var item=new CutoverChecklistItemDO();item.setId(30L);item.setTenantId(1L);item.setChecklistId(20L);item.setApplicableFlag(true);item.setWorkModeCode("COLLECTION");
        var device=new CutoverTaskDeviceScopeDO();device.setDeviceId(31L);device.setSerialNumberSnapshot("test-sn");
        when(tasks.selectById(10L)).thenReturn(task);when(tasks.selectForUpdate(any())).thenReturn(task);
        when(checklists.selectById(20L)).thenReturn(list);when(checklists.selectCurrentForUpdate(any())).thenReturn(list);
        when(items.selectById(30L)).thenReturn(item);when(items.selectListForUpdate(any())).thenReturn(List.of(item));
        when(devices.byTask(any())).thenReturn(List.of(device));
        when(permissions.hasAnyPermissions(anyLong(),anyString())).thenReturn(true);
        when(scopes.inspect(anyLong(),anyLong(),anyString())).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(21L,4L,true));
        when(scopes.lockAndRevalidate(any())).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(21L,4L,true));
    }
    @Test void editableChecklistAllowsManualCommandsWithoutAdvancingBusinessState(){
        var context=source.authorize(query(7L,3L));
        assertTrue(context.manualAllowed());assertTrue(context.canExecute());assertEquals(31L,context.deviceId());
        assertEquals("P3",task.getCurrentStage());assertEquals("SURVEYING",task.getTaskStatus());
        verify(tasks,never()).updateById(any(CutoverTaskDO.class));
    }
    @Test void enablingManualCommandsRetainsOwnerAndVersionChecks(){
        assertThrows(AccessDeniedException.class,()->source.authorize(query(8L,3L)));
        assertThrows(CollectionOperationException.class,()->source.authorize(query(7L,2L)));
        task.setCurrentStage("P4");
        assertThrows(CollectionOperationException.class,()->source.authorize(query(7L,3L)));
    }
    private CollectionSourceAdapter.AuthorizationQuery query(Long actor,Long version){
        return CollectionSourceAdapter.AuthorizationQuery.builder().tenantId(1L).actorId(actor).objectId(30L).deviceId(31L)
                .access(CollectionSourceAdapter.Access.EXECUTE).expectedVersion(version).build();
    }
}
