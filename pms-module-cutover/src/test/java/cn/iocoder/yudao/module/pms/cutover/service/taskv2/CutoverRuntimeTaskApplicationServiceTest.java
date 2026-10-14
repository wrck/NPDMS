package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceProductTypePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class CutoverRuntimeTaskApplicationServiceTest {

    @Test void explicitProjectSelectionUsesOnlyThatProjectsMatchingSerial() {
        var fixture = fixture();
        when(fixture.deviceScope().resolveBySerials(List.of("SN-400"))).thenReturn(List.of(
                new CutoverDeviceScopePort.DeviceFact(400L, "SN-400", 100L, 9L),
                new CutoverDeviceScopePort.DeviceFact(401L, "SN-400", 101L, 2L)));
        fixture.service().create(createCommand("selected-project"));
        assertEquals(List.of(400L), fixture.deviceRows().stream().map(CutoverTaskDeviceScopeDO::getDeviceId).toList());
    }

    @Test void draftKeepsFrozenContextWhenProjectReferenceIsUnavailableButSubmitStillRevalidates() {
        var fixture = fixture();
        var created = fixture.service().create(createCommand("reference-outage"));
        clearInvocations(fixture.projectContext());
        when(fixture.projectContext().lockAndRevalidate(any())).thenThrow(
                new cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException(
                        cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE, "项目参考暂不可用"));
        var saved = fixture.service().saveAssessment(saveCommand(created.taskId(), created.version(), "B"));
        verifyNoInteractions(fixture.projectContext());
        assertEquals("DRAFT", fixture.assessment().get().getAssessmentStatus());
        assertThrows(cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException.class,
                () -> fixture.service().submitAssessment(submitCommand(created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "submit-outage")));
        assertEquals("P2", fixture.task().get().getCurrentStage());
    }

    @Test
    void completesSelfCreatedAssessmentAIntoP3WithControlledOwnerFacts() {
        Fixture fixture = fixture();

        CutoverTaskCommandResult created = fixture.service().create(createCommand("create-a"));
        CutoverAssessmentCommandResult saved = fixture.service().saveAssessment(saveCommand(
                created.taskId(), created.version(), "A"));
        CutoverTaskCommandResult submitted = fixture.service().submitAssessment(submitCommand(
                created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "submit-a"));

        assertEquals("P2", created.currentStage());
        assertEquals("P3", submitted.currentStage());
        assertEquals("SURVEYING", submitted.taskStatus());
        assertFalse(fixture.assessment().get().getSimpleFlow());
        assertEquals(List.of("P1_ACCEPTED", "P2_ASSESSMENT_SUBMITTED"), fixture.history().stream()
                .map(CutoverTaskStageHistoryDO::getTriggerType).toList());
        assertEquals(2, fixture.platform().facts.size());
        assertNull(fixture.deviceRows().getFirst().getDeviceTypeCodeSnapshot());
        assertNull(fixture.deviceRows().getFirst().getDeviceTypeSourceVersionSnapshot());
        assertFalse(fixture.deviceWrites().isEmpty());
        fixture.deviceWrites().forEach(write -> assertTrue(write.productTypes().isEmpty()));
        verifyNoInteractions(fixture.productType());
    }

    @Test
    void completesSelfCreatedAssessmentDIntoP4WithControlledOwnerFacts() {
        Fixture fixture = fixture();

        CutoverTaskCommandResult created = fixture.service().create(createCommand("create-d"));
        CutoverAssessmentCommandResult saved = fixture.service().saveAssessment(saveCommand(
                created.taskId(), created.version(), "D"));
        CutoverTaskCommandResult submitted = fixture.service().submitAssessment(submitCommand(
                created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "submit-d"));

        assertEquals("P4", submitted.currentStage());
        assertEquals("PLAN_DRAFTING", submitted.taskStatus());
        assertTrue(fixture.assessment().get().getSimpleFlow());
    }

    @Test
    void selfCreatedTaskWithoutCustomerCompletesAssessmentDWithoutCustomerProvider() {
        Fixture fixture = fixture();
        var source = createCommand("no-customer");
        var originalProject = source.expectedContext().project();
        var project = CutoverProjectContextPort.ProjectContextFact.builder()
                .tenantId(originalProject.tenantId())
                .projectId(originalProject.projectId())
                .projectVersion(originalProject.projectVersion())
                .projectCode(originalProject.projectCode())
                .projectName(originalProject.projectName())
                .customerId(null).customerCode(null).customerName(null)
                .departmentId(originalProject.departmentId())
                .departmentCode(originalProject.departmentCode())
                .departmentName(originalProject.departmentName())
                .projectScopeVersion(originalProject.projectScopeVersion())
                .build();
        when(fixture.projectContext().inspect(any())).thenReturn(project);
        when(fixture.projectContext().lockAndRevalidate(any())).thenReturn(project);
        var command = new CreateCutoverTaskCommand(source.tenantId(), source.actorId(), source.idempotencyKey(),
                source.correlationId(), source.intakeSourceType(), source.projectId(), source.serialNumbers(),
                source.configurationCode(), source.taskName(), source.background(), source.cutoverType(),
                source.networkMode(), source.scheduledTime(), source.sourceSystem(), source.sourceBusinessNo(),
                source.businessEventId(), new CreateCutoverTaskCommand.ExpectedCreateContext(project,
                source.expectedContext().devices(),
                new CutoverCustomerLevelPort.CustomerLevelFact("UNAVAILABLE", null, null, null,
                        null, null, 0L, null, null), source.expectedContext().readiness()));

        var created = fixture.service().create(command);
        var saved = fixture.service().saveAssessment(saveCommand(created.taskId(), created.version(), "D"));
        var submitted = fixture.service().submitAssessment(submitCommand(
                created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "no-customer-submit"));

        assertEquals("P4", submitted.currentStage());
        assertEquals("UNAVAILABLE", fixture.assessment().get().getCustomerFactStatusCode());
        verifyNoInteractions(fixture.customerLevel());
    }

    @Test
    void selfCreatedTaskWithoutProductTypeProviderCompletesAssessmentD() {
        Fixture fixture = fixture();
        when(fixture.productType().resolveAuthorized(8L, List.of(400L)))
                .thenThrow(new AssertionError("独立任务不得调用设备产品类型参考"));

        var created = fixture.service().create(createCommand("no-product-type"));
        var saved = fixture.service().saveAssessment(saveCommand(created.taskId(), created.version(), "D"));
        var submitted = fixture.service().submitAssessment(submitCommand(
                created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "no-product-type-submit"));

        assertEquals("P4", submitted.currentStage());
        assertFalse(fixture.deviceRows().isEmpty());
        for (var device : fixture.deviceRows()) {
            assertNull(device.getDeviceTypeCodeSnapshot());
            assertNull(device.getDeviceTypeSourceVersionSnapshot());
        }
        assertFalse(fixture.deviceWrites().isEmpty());
        fixture.deviceWrites().forEach(write -> assertTrue(write.productTypes().isEmpty()));
        verifyNoInteractions(fixture.productType());
    }

    @Test
    void replaysCompletedCreateBeforeReadingMutableOwnerFacts() {
        Fixture fixture = fixture();
        CreateCutoverTaskCommand command = createCommand("create-replay");

        CutoverTaskCommandResult first = fixture.service().create(command);
        int writesBeforeReplay = fixture.deviceWrites().size();
        clearInvocations(fixture.productType());
        CutoverTaskCommandResult replay = fixture.service().create(command);

        assertEquals(first.taskId(), replay.taskId());
        assertTrue(replay.replayed());
        assertEquals(writesBeforeReplay, fixture.deviceWrites().size());
        verifyNoInteractions(fixture.productType());
    }

    @Test
    void independentTaskCompletesWhenReadinessAndCustomerReferenceAreUnavailable() {
        Fixture fixture = fixture();
        when(fixture.customerLevel().inspect(any())).thenThrow(
                new cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException(
                        cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE,
                        "客户参考信息暂不可用"));
        when(fixture.readiness().inspect(any(), any())).thenThrow(new AssertionError("独立任务不得调用IMP"));
        when(fixture.readiness().lockAndRevalidate(any())).thenThrow(new AssertionError("独立任务不得锁IMP"));
        var created = fixture.service().create(createCommand("independent"));
        var saved = fixture.service().saveAssessment(saveCommand(created.taskId(), created.version(), "D"));
        var result = fixture.service().submitAssessment(submitCommand(
                created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "independent-submit"));
        assertEquals("P4", result.currentStage());
        assertNull(fixture.task().get().getImplementationReadinessSnapshotId());
        assertNull(fixture.task().get().getImplementationReadinessSnapshotVersion());
        assertEquals("NOT_APPLICABLE", fixture.task().get().getReadinessDecisionCode());
        assertEquals("UNAVAILABLE", fixture.assessment().get().getCustomerFactStatusCode());
        verifyNoInteractions(fixture.readiness());
        verify(fixture.customerLevel(), never()).lockAndRevalidate(any());
    }

    @Test
    void itrIntakeDoesNotRequireImplementationReadiness() {
        Fixture fixture = fixture();
        var source = createCommand("itr");
        var command = new CreateCutoverTaskCommand(source.tenantId(), source.actorId(), source.idempotencyKey(),
                source.correlationId(), "ITR", source.projectId(), source.serialNumbers(), source.configurationCode(),
                source.taskName(), source.background(), source.cutoverType(), source.networkMode(), source.scheduledTime(),
                "ITR", "ITR-001", null, null);
        var created = fixture.service().create(command);
        assertEquals("P2", created.currentStage());
        assertEquals("ITR", fixture.task().get().getIntakeSourceType());
        verifyNoInteractions(fixture.readiness());
    }

    @Test
    void projectIntakeStillRejectsUnreadyImplementationBeforeCreatingTask() {
        Fixture fixture = fixture();
        when(fixture.readiness().inspect(any(), any())).thenReturn(
                new CutoverReadinessPort.ReadinessFact(600L, 4L, "NOT_READY", 100L,
                        List.of(400L), "watermark-1", List.of("EXE_04_NOT_COMPLETED")));
        var source = createCommand("project");
        var command = new CreateCutoverTaskCommand(source.tenantId(), source.actorId(), source.idempotencyKey(),
                source.correlationId(), "PROJECT_EVENT", source.projectId(), source.serialNumbers(),
                source.configurationCode(), source.taskName(), source.background(), source.cutoverType(),
                source.networkMode(), source.scheduledTime(), null, null, "project-event-1", null);
        var exception = assertThrows(CutoverTaskApplicationException.class, () -> fixture.service().create(command));
        assertEquals(CutoverTaskApplicationException.Code.READINESS_NOT_READY, exception.code());
        assertNull(fixture.task().get());
        assertTrue(fixture.history().isEmpty());
    }

    @Test
    void independentTaskStillRejectsAnUnauthorizedProject() {
        Fixture fixture = fixture();
        when(fixture.projectScope().inspect(8L, 100L, "ACTION_EDIT")).thenReturn(
                new CutoverProjectScopePort.ProjectScopeFact(100L, 7L, false));
        var exception = assertThrows(CutoverTaskApplicationException.class,
                () -> fixture.service().create(createCommand("denied")));
        assertEquals(CutoverTaskApplicationException.Code.DATA_SCOPE_FORBIDDEN, exception.code());
        assertNull(fixture.task().get());
    }

    private static Fixture fixture() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverTaskDeviceScopeMapper deviceMapper = mock(CutoverTaskDeviceScopeMapper.class);
        CutoverTaskStageHistoryMapper historyMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverAssessmentMapper assessmentMapper = mock(CutoverAssessmentMapper.class);
        CutoverConfigurationRevisionMapper configurationMapper = mock(CutoverConfigurationRevisionMapper.class);
        CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        CutoverProjectContextPort projectContext = mock(CutoverProjectContextPort.class);
        CutoverDeviceScopePort deviceScope = mock(CutoverDeviceScopePort.class);
        CutoverDeviceProductTypePort productType = mock(CutoverDeviceProductTypePort.class);
        CutoverCustomerLevelPort customerLevel = mock(CutoverCustomerLevelPort.class);
        CutoverReadinessPort readiness = mock(CutoverReadinessPort.class);
        CutoverTaskSnapshotStore snapshots = mock(CutoverTaskSnapshotStore.class);
        List<CutoverTaskSnapshotStore.DeviceWrite> deviceWrites = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> { deviceWrites.add(invocation.getArgument(0)); return null; })
                .when(snapshots).replaceAssessmentDevices(any());
        DirectPlatform platform = new DirectPlatform();
        AtomicReference<CutoverTaskDO> task = new AtomicReference<>();
        AtomicReference<CutoverAssessmentDO> assessment = new AtomicReference<>();
        List<CutoverTaskDeviceScopeDO> deviceRows = new ArrayList<>();
        List<CutoverTaskStageHistoryDO> history = new ArrayList<>();

        CutoverProjectScopePort.ProjectScopeFact scopeFact =
                new CutoverProjectScopePort.ProjectScopeFact(100L, 7L, true);
        CutoverProjectContextPort.ProjectContextFact projectFact =
                CutoverProjectContextPort.ProjectContextFact.builder()
                        .tenantId(1L)
                        .projectId(100L)
                        .projectVersion(3)
                        .projectCode("PROJ-100")
                        .projectName("核心网割接项目")
                        .customerId(200L)
                        .customerCode("CUS-200")
                        .customerName("示例客户")
                        .departmentId(300L)
                        .departmentCode("OFFICE-300")
                        .departmentName("一号办事处")
                        .projectScopeVersion(7L)
                        .build();
        List<CutoverDeviceScopePort.DeviceFact> devices = List.of(
                new CutoverDeviceScopePort.DeviceFact(400L, "SN-400", 100L, 9L));
        List<CutoverDeviceProductTypePort.ProductTypeFact> productTypes = List.of(
                new CutoverDeviceProductTypePort.ProductTypeFact(400L, "ROUTER", true, "pt-v1",
                        "RESOLVED", "FRESH", LocalDateTime.of(2026, 8, 31, 0, 0), false));
        CutoverCustomerLevelPort.CustomerLevelFact customerFact =
                new CutoverCustomerLevelPort.CustomerLevelFact("AVAILABLE", 200L, "CUS-200", "示例客户",
                        500L, "LEVEL_1", 2L, LocalDateTime.of(2026, 8, 1, 0, 0), null);
        CutoverReadinessPort.ReadinessFact readinessFact =
                new CutoverReadinessPort.ReadinessFact(600L, 4L, "READY", 100L,
                        List.of(400L), "watermark-1", List.of());

        when(projectScope.inspect(8L, 100L, "ACTION_EDIT")).thenReturn(scopeFact);
        when(projectScope.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(8L)
                .projectId(100L)
                .action("ACTION_EDIT")
                .expectedProjectScopeVersion(7L)
                .build())).thenReturn(scopeFact);
        when(projectContext.inspect(CutoverProjectContextPort.Inspection.builder()
                .tenantId(1L)
                .projectId(100L)
                .expectedProjectScopeVersion(7L)
                .build())).thenReturn(projectFact);
        when(projectContext.lockAndRevalidate(projectFact)).thenReturn(projectFact);
        when(deviceScope.resolveBySerials(List.of("SN-400"))).thenReturn(devices);
        when(deviceScope.lockAndRevalidate(100L, devices)).thenReturn(devices);
        when(productType.resolveAuthorized(8L, List.of(400L))).thenReturn(productTypes);
        when(customerLevel.inspect(200L)).thenReturn(customerFact);
        when(customerLevel.lockAndRevalidate(customerFact)).thenReturn(customerFact);
        when(snapshots.readiness(any())).thenAnswer(invocation -> {
            CutoverTaskDO row = invocation.getArgument(0);
            return "NOT_APPLICABLE".equals(row.getReadinessDecisionCode())
                    ? CutoverRuntimeContextSupport.independentReadiness(row.getProjectId(), List.of(400L)) : readinessFact;
        });
        when(readiness.inspect(100L, List.of(400L))).thenReturn(readinessFact);
        when(readiness.lockAndRevalidate(readinessFact)).thenReturn(readinessFact);
        when(deviceMapper.selectActiveForUpdate(any())).thenReturn(List.of());
        CutoverConfigurationRevisionDO configuration = new CutoverConfigurationRevisionDO();
        configuration.setId(700L);
        configuration.setConfigurationCode("CUTOVER-V1");
        configuration.setRevisionNo(1);
        when(configurationMapper.selectEffectivePublished(any())).thenReturn(configuration);
        when(deviceMapper.selectActiveByTask(any())).thenAnswer(ignored -> List.copyOf(deviceRows));
        when(taskMapper.selectById(any())).thenAnswer(ignored -> task.get());
        when(taskMapper.selectForUpdate(any())).thenAnswer(ignored -> task.get());
        when(assessmentMapper.selectForUpdate(any())).thenAnswer(ignored -> assessment.get());
        when(taskMapper.insert(any(CutoverTaskDO.class))).thenAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return 1;
        });
        when(deviceMapper.insert(any(CutoverTaskDeviceScopeDO.class))).thenAnswer(invocation -> {
            deviceRows.add(invocation.getArgument(0));
            return 1;
        });
        when(historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenAnswer(invocation -> {
            history.add(invocation.getArgument(0));
            return 1;
        });
        when(assessmentMapper.insert(any(CutoverAssessmentDO.class))).thenAnswer(invocation -> {
            assessment.set(invocation.getArgument(0));
            return 1;
        });
        when(taskMapper.linkAssessmentIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskAssessmentLinkUpdate)
                    invocation.getArgument(0);
            task.get().setCurrentAssessmentId(update.assessmentId());
            task.get().setVersion(update.expectedVersion() + 1);
            return 1;
        });
        when(assessmentMapper.submitIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentSubmitUpdate)
                    invocation.getArgument(0);
            assessment.get().setAssessmentStatus("SUBMITTED");
            assessment.get().setManualGrade(update.assessment().getManualGrade());
            assessment.get().setSimpleFlow(update.assessment().getSimpleFlow());
            return 1;
        });
        when(taskMapper.transitionIfMatch(any())).thenReturn(1);

        CutoverRuntimeTaskApplicationService service = new CutoverRuntimeTaskApplicationService(taskMapper, deviceMapper,
                historyMapper, assessmentMapper, configurationMapper, projectScope, projectContext,
                deviceScope, productType, customerLevel,
                readiness, platform, Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC), mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "taskSnapshots", snapshots);
        return new Fixture(service, task, assessment, deviceRows, history, platform, productType, readiness, customerLevel, projectScope, projectContext, deviceScope, deviceWrites);
    }

    private static CreateCutoverTaskCommand createCommand(String key) {
        return new CreateCutoverTaskCommand(1L, 8L, key, "corr-" + key, "SELF_CREATED", 100L,
                List.of("SN-400"), "CUTOVER-V1", "核心网割接", "计划内设备割接", "配置变更", "普通双机",
                LocalDateTime.of(2026, 9, 1, 1, 0), null, null, null,
                new CreateCutoverTaskCommand.ExpectedCreateContext(
                        CutoverProjectContextPort.ProjectContextFact.builder()
                                .tenantId(1L)
                                .projectId(100L)
                                .projectVersion(3)
                                .projectCode("PROJ-100")
                                .projectName("核心网割接项目")
                                .customerId(200L)
                                .customerCode("CUS-200")
                                .customerName("示例客户")
                                .departmentId(300L)
                                .departmentCode("OFFICE-300")
                                .departmentName("一号办事处")
                                .projectScopeVersion(7L)
                                .build(),
                        List.of(new CutoverDeviceScopePort.DeviceFact(400L, "SN-400", 100L, 9L)),
                        new CutoverCustomerLevelPort.CustomerLevelFact("AVAILABLE", 200L, "CUS-200", "示例客户",
                                500L, "LEVEL_1", 2L, LocalDateTime.of(2026, 8, 1, 0, 0), null),
                        new CutoverReadinessPort.ReadinessFact(600L, 4L, "READY", 100L,
                                List.of(400L), "watermark-1", List.of())));
    }

    private static SaveCutoverAssessmentCommand saveCommand(Long taskId, Long taskVersion, String grade) {
        return new SaveCutoverAssessmentCommand(1L, 8L, taskId, taskVersion, 0L,
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true), grade, "corr-save-" + grade);
    }

    private static SubmitCutoverAssessmentCommand submitCommand(Long taskId, Long taskVersion,
                                                                 Long assessmentVersion, String key) {
        return new SubmitCutoverAssessmentCommand(1L, 8L, taskId, taskVersion, assessmentVersion,
                key, "corr-" + key);
    }

    private record Fixture(CutoverRuntimeTaskApplicationService service, AtomicReference<CutoverTaskDO> task,
                           AtomicReference<CutoverAssessmentDO> assessment,
                           List<CutoverTaskDeviceScopeDO> deviceRows,
                           List<CutoverTaskStageHistoryDO> history, DirectPlatform platform,
                           CutoverDeviceProductTypePort productType, CutoverReadinessPort readiness,
                           CutoverCustomerLevelPort customerLevel, CutoverProjectScopePort projectScope, CutoverProjectContextPort projectContext,
                           CutoverDeviceScopePort deviceScope, List<CutoverTaskSnapshotStore.DeviceWrite> deviceWrites) {
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final List<SuccessFacts> facts = new ArrayList<>();
        private final Map<String, CachedResult> completed = new HashMap<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest, Class<T> responseType,
                                              Supplier<T> operation, Function<T, SuccessFacts> successFactsFactory) {
            String key = scope.scopeCode() + ":" + scope.actorId() + ":" + scope.key();
            CachedResult cached = completed.get(key);
            if (cached != null) {
                if (!cached.requestDigest().equals(requestDigest)) {
                    return new ExecutionResult<>(Decision.CONFLICT, null);
                }
                return new ExecutionResult<>(Decision.REPLAY_COMPLETED, responseType.cast(cached.response()));
            }
            T result = operation.get();
            facts.add(successFactsFactory.apply(result));
            completed.put(key, new CachedResult(requestDigest, result));
            return new ExecutionResult<>(Decision.NEW, result);
        }

        private record CachedResult(String requestDigest, Object response) {
        }
    }
}
