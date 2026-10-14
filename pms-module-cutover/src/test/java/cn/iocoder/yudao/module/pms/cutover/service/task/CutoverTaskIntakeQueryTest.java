package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverTaskIntakeQueryTest {
    @Test
    void inheritedDetailKeepsArchivedDevicesAndSkippedDStage() {
        var tasks = mock(CutoverTaskMapper.class);
        var activeDevices = mock(CutoverTaskDeviceScopeMapper.class);
        var frozenDevices = mock(CutoverFrozenDeviceMapper.class);
        var scopes = mock(CutoverProjectScopePort.class);
        CutoverTaskQueryService queries = new CutoverTaskQueryService(tasks, activeDevices,
                mock(CutoverAssessmentMapper.class), mock(CutoverChecklistMapper.class),
                mock(CutoverConfigurationRevisionMapper.class), scopes, mock(CutoverProjectContextPort.class),
                mock(CutoverDeviceScopePort.class), mock(CutoverCustomerLevelPort.class),
                mock(CutoverReadinessPort.class), Clock.systemUTC(), frozenDevices);
        org.springframework.test.util.ReflectionTestUtils.setField(queries, "taskSnapshots",
                mock(CutoverTaskSnapshotStore.class));
        var task = new CutoverTaskDO();
        task.setId(701L); task.setTenantId(1L); task.setProjectId(101L);
        task.setCurrentStage("P6"); task.setTaskStatus("ARCHIVED"); task.setManualGrade("D");
        when(tasks.selectById(701L)).thenReturn(task);
        when(scopes.inspect(9L, 101L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(101L, 7L, true));
        var device = new CutoverTaskDeviceScopeDO();
        device.setDeviceId(401L); device.setSerialNumberSnapshot("SN-HISTORICAL");
        device.setProjectId(101L); device.setProjectAssignmentVersion(5L);
        when(frozenDevices.byTask(any())).thenReturn(List.of(device));

        var detail = queries.detail(new CutoverTaskContext(1L, 9L, "archived-query",
                false, false, false, false, false), 701L);

        assertThat(detail.devices()).singleElement().satisfies(value ->
                assertThat(value.serialNumber()).isEqualTo("SN-HISTORICAL"));
        assertThat(detail.workbenchSteps()).filteredOn(value -> "P3".equals(value.stage()))
                .singleElement().satisfies(value -> assertThat(value.state()).isEqualTo("SKIPPED"));
        assertThat(detail.allowedActions()).isEmpty();
        verifyNoInteractions(activeDevices);
    }
}
