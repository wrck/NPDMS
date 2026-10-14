package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CutoverTaskQueryServiceTest {

    @Mock private CutoverTaskMapper taskMapper;
    @Mock private CutoverTaskDeviceScopeMapper deviceMapper;
    @Mock private CutoverAssessmentMapper assessmentMapper;
    @Mock private CutoverChecklistMapper checklistMapper;
    @Mock private CutoverConfigurationRevisionMapper configurationMapper;
    @Mock private CutoverProjectScopePort projectScopePort;
    @Mock private CutoverProjectContextPort projectContextPort;
    @Mock private CutoverDeviceScopePort deviceScopePort;
    @Mock private CutoverCustomerLevelPort customerLevelPort;
    @Mock private CutoverReadinessPort readinessPort;
    @Mock private CutoverTaskSnapshotStore snapshots;
    @Mock private cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper frozenDevices;

    private CutoverTaskQueryService service;
    private CutoverProjectContextPort.ProjectContextFact project;
    private CutoverDeviceScopePort.DeviceFact device;
    private CutoverCustomerLevelPort.CustomerLevelFact customer;
    private CutoverReadinessPort.ReadinessFact readiness;

    @BeforeEach
    void setUp() {
        service = new CutoverTaskQueryService(taskMapper, deviceMapper, assessmentMapper, checklistMapper,
                configurationMapper, projectScopePort, projectContextPort, deviceScopePort, customerLevelPort,
                readinessPort, Clock.fixed(Instant.parse("2026-08-31T04:00:00Z"), ZoneOffset.UTC),
                frozenDevices);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "taskSnapshots", snapshots);
        project = CutoverProjectContextPort.ProjectContextFact.builder()
                .tenantId(1L)
                .projectId(101L)
                .projectVersion(3)
                .projectCode("P-101")
                .projectName("核心网扩容")
                .customerId(201L)
                .customerCode("C-201")
                .customerName("示例客户")
                .departmentId(301L)
                .departmentCode("OFF-01")
                .departmentName("华东办事处")
                .projectScopeVersion(7L)
                .build();
        device = new CutoverDeviceScopePort.DeviceFact(401L, "SN-401", 101L, 5L);
        customer = new CutoverCustomerLevelPort.CustomerLevelFact("AVAILABLE", 201L, "C-201", "示例客户",
                501L, "GOLD", 2L, LocalDateTime.of(2026, 8, 1, 0, 0), null);
        readiness = new CutoverReadinessPort.ReadinessFact(601L, 4L, "READY", 101L,
                List.of(401L), List.of("W-1"), List.of());
    }

    @Test
    void resolvesSingleAuthorizedCreateCandidate() {
        when(deviceScopePort.resolveBySerials(List.of("SN-401"))).thenReturn(List.of(device));
        when(projectScopePort.inspect(9L, 101L, "ACTION_EDIT"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(101L, 7L, true));
        when(projectContextPort.inspect(CutoverProjectContextPort.Inspection.builder()
                .tenantId(1L)
                .projectId(101L)
                .expectedProjectScopeVersion(7L)
                .build())).thenReturn(project);
        when(customerLevelPort.inspect(201L)).thenReturn(customer);
        when(configurationMapper.selectEffectivePublishedList(any())).thenReturn(List.of(configuration()));

        CutoverTaskViews.CreateContextData result = service.resolveCreateContext(new CutoverTaskContext(1L, 9L, "query-test", true, true, false, false, false), List.of("SN-401"));

        assertThat(result.selectionRequired()).isFalse();
        assertThat(result.configurationChoices()).singleElement().satisfies(choice ->
                assertThat(choice.configurationCode()).isEqualTo("CUTOVER-V1"));
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.project()).isEqualTo(project);
            assertThat(candidate.devices()).containsExactly(device);
            assertThat(candidate.createAllowed()).isTrue();
        });
    }

    @Test
    void returnsAuthorizedP2Detail() {
        CutoverTaskDO task = task();

        when(taskMapper.selectById(701L)).thenReturn(task);
        when(projectScopePort.inspect(9L, 101L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(101L, 7L, true));
        when(projectScopePort.inspect(9L, 101L, "ACTION_EDIT"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(101L, 7L, true));
        when(projectContextPort.inspect(CutoverProjectContextPort.Inspection.builder()
                .tenantId(1L)
                .projectId(101L)
                .expectedProjectScopeVersion(7L)
                .build())).thenReturn(project);
        when(deviceScopePort.resolveBySerials(List.of("SN-401"))).thenReturn(List.of(device));
        when(frozenDevices.byTask(any())).thenReturn(List.of(deviceRow()));
        when(assessmentMapper.selectById(801L)).thenReturn(assessment());

        CutoverTaskViews.Detail detail = service.detail(new CutoverTaskContext(1L, 9L, "query-test", true, true, false, false, false), 701L);

        assertThat(detail.devices()).containsExactly(device);
        assertThat(detail.allowedActions()).containsExactly("SAVE_ASSESSMENT", "SUBMIT_ASSESSMENT");
        assertThat(detail.workbenchSteps()).extracting(CutoverTaskViews.WorkbenchStep::stage)
                .containsExactly("P2", "P3", "P4", "P5", "P6");
        assertThat(detail.workbenchSteps().getFirst().isCurrent()).isTrue();
    }

    @Test
    void exposesOnlyServerAuthorizedP3ChecklistActions() {
        CutoverTaskDO task = task();
        task.setCurrentStage("P3");
        task.setTaskStatus("SURVEYING");
        task.setManualGrade("A");
        when(taskMapper.selectById(701L)).thenReturn(task);
        when(projectScopePort.inspect(9L, 101L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(101L, 7L, true));
        when(frozenDevices.byTask(any())).thenReturn(List.of(deviceRow()));
        when(assessmentMapper.selectById(801L)).thenReturn(assessment());
        cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO checklist =
                new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO();
        checklist.setStatusCode("DRAFT");
        when(checklistMapper.selectCurrent(any())).thenReturn(checklist);

        CutoverTaskViews.Detail detail = service.detail(new CutoverTaskContext(1L, 9L, "query-test", false, false, true, true, true), 701L);

        assertThat(detail.allowedActions()).containsExactly(
                "SAVE_CHECKLIST", "REQUEST_COLLECTION", "SUBMIT_CHECKLIST");
    }

    private CutoverTaskSnapshotStore.Context frozenContext() {
        return CutoverTaskSnapshotStore.Context.builder().project(project).customer(customer).readiness(readiness)
                .intakeSourceType("SELF_CREATED").build();
    }

    private CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO();
        row.setId(701L);
        row.setTenantId(1L);
        row.setProjectId(101L);
        row.setTaskNo("CUT-701");
        row.setTaskName("核心网割接");
        row.setBackground("设备替换");
        row.setTaskOrigin("NEW_PLATFORM");
        row.setIntakeSourceType("SELF_CREATED");
        row.setCutoverType("CORE_REPLACEMENT");
        row.setNetworkMode("DUAL_PLANE");
        row.setOwnerUserId(9L);
        row.setCustomerId(201L);
        row.setCurrentStage("P2");
        row.setTaskStatus("GRADE_CONFIRMING");
        row.setProjectScopeVersion(7L);
        CutoverTaskSnapshotStore.applyTask(new CutoverTaskSnapshotStore.TaskWrite(row, frozenContext()));
        when(snapshots.readiness(row)).thenReturn(readiness);
        row.setCurrentAssessmentId(801L);
        row.setScheduledTime(LocalDateTime.of(2026, 9, 1, 1, 30));
        row.setCreateTime(LocalDateTime.of(2026, 8, 31, 4, 0));
        row.setVersion(1L);
        return row;
    }

    private CutoverTaskDeviceScopeDO deviceRow() {
        CutoverTaskDeviceScopeDO row = new CutoverTaskDeviceScopeDO();
        row.setTenantId(1L);
        row.setCutoverTaskId(701L);
        row.setProjectId(101L);
        row.setDeviceId(401L);
        row.setSerialNumberSnapshot("SN-401");
        row.setProjectAssignmentVersion(5L);
        return row;
    }

    private CutoverAssessmentDO assessment() {
        CutoverAssessmentDO row = new CutoverAssessmentDO();
        row.setId(801L);
        row.setTenantId(1L);
        row.setCutoverTaskId(701L);
        row.setAssessmentVersion(1L);
        row.setAssessmentStatus("DRAFT");
        row.setQuestionnaireTemplateCode("CUT_P2_MANUAL_ASSESSMENT");
        row.setQuestionnaireTemplateVersion(1L);
        CutoverTaskSnapshotStore.applyAssessment(new CutoverTaskSnapshotStore.AssessmentWrite(row, frozenContext(),
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true)));
        row.setManualGrade("A");
        row.setSimpleFlow(false);
        row.setVersion(0L);
        return row;
    }

    private CutoverConfigurationRevisionDO configuration() {
        CutoverConfigurationRevisionDO row = new CutoverConfigurationRevisionDO();
        row.setId(901L);
        row.setConfigurationCode("CUTOVER-V1");
        row.setConfigurationName("标准割接配置");
        row.setRevisionNo(1);
        row.setEffectiveFrom(LocalDateTime.of(2026, 8, 1, 0, 0));
        return row;
    }
}
