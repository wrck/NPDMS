package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceProductTypePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CutoverTaskApplicationServiceTest {

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
        assertEquals(3, fixture.platform().facts.size());
        assertNull(fixture.deviceRows().getFirst().getDeviceTypeCodeSnapshot());
        assertNull(fixture.deviceRows().getFirst().getDeviceTypeSourceVersionSnapshot());
        assertFalse(fixture.deviceWrites().isEmpty());
        fixture.deviceWrites().forEach(write -> {
            assertTrue(write.productTypes().isEmpty());
        });
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
    void replaysCompletedCreateBeforeReadingMutableOwnerFacts() {
        Fixture fixture = fixture();
        CreateCutoverTaskCommand command = createCommand("create-replay");

        CutoverTaskCommandResult first = fixture.service().create(command);
        clearInvocations(fixture.productType());
        CutoverTaskCommandResult replay = fixture.service().create(command);

        assertEquals(first.taskId(), replay.taskId());
        assertTrue(replay.replayed());
        verifyNoInteractions(fixture.productType());
    }

    @Test
    void assessmentSaveReplaysWithoutAppendingFactsAndStillChecksCurrentOwner() {
        Fixture fixture = fixture();
        var created = fixture.service().create(createCommand("create-save-replay"));
        var command = saveCommand(created.taskId(), created.version(), "B");
        var first = fixture.service().saveAssessment(command);
        int writes = fixture.deviceWrites().size();
        assertEquals(first, fixture.service().saveAssessment(command));
        assertEquals(writes, fixture.deviceWrites().size());
        assertEquals(2, fixture.platform().facts.size());
        var changed = new SaveCutoverAssessmentCommand(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.expectedAssessmentVersion(), command.answers(), "A",
                command.idempotencyKey(), command.correlationId());
        assertEquals(CutoverTaskApplicationException.Code.IDEMPOTENCY_CONFLICT,
                org.junit.jupiter.api.Assertions.assertThrows(CutoverTaskApplicationException.class,
                        () -> fixture.service().saveAssessment(changed)).code());
        fixture.task().get().setOwnerUserId(999L);
        assertEquals(CutoverTaskApplicationException.Code.NOT_FOUND,
                org.junit.jupiter.api.Assertions.assertThrows(CutoverTaskApplicationException.class,
                        () -> fixture.service().saveAssessment(command)).code());
        assertEquals(writes, fixture.deviceWrites().size());
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
                    ? CutoverContextSupport.independentReadiness(row.getProjectId(), List.of(400L)) : readinessFact;
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
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskAssessmentLinkUpdate)
                    invocation.getArgument(0);
            task.get().setCurrentAssessmentId(update.assessmentId());
            task.get().setVersion(update.expectedVersion() + 1);
            return 1;
        });
        when(assessmentMapper.submitIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentSubmitUpdate)
                    invocation.getArgument(0);
            assessment.get().setAssessmentStatus("SUBMITTED");
            assessment.get().setManualGrade(update.assessment().getManualGrade());
            assessment.get().setSimpleFlow(update.assessment().getSimpleFlow());
            return 1;
        });
        when(taskMapper.transitionIfMatch(any())).thenReturn(1);

        CutoverTaskApplicationService service = new CutoverTaskApplicationService(taskMapper,
                deviceMapper,
                historyMapper,
                assessmentMapper,
                configurationMapper,
                projectScope,
                projectContext,
                deviceScope,
                productType,
                customerLevel,
                readiness,
                platform,
                Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "taskSnapshots", snapshots);
        return new Fixture(service, task, assessment, deviceRows, history, platform, productType, deviceWrites);
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
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true), grade, "save-" + grade, "corr-save-" + grade);
    }

    private static SubmitCutoverAssessmentCommand submitCommand(Long taskId, Long taskVersion,
                                                                 Long assessmentVersion, String key) {
        return new SubmitCutoverAssessmentCommand(1L, 8L, taskId, taskVersion, assessmentVersion,
                key, "corr-" + key);
    }

    private record Fixture(CutoverTaskApplicationService service, AtomicReference<CutoverTaskDO> task,
                           AtomicReference<CutoverAssessmentDO> assessment,
                           List<CutoverTaskDeviceScopeDO> deviceRows,
                           List<CutoverTaskStageHistoryDO> history, DirectPlatform platform,
                           CutoverDeviceProductTypePort productType, List<CutoverTaskSnapshotStore.DeviceWrite> deviceWrites) {
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
