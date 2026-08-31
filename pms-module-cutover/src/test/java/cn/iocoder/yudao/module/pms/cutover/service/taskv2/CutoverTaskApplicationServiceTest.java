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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        assertEquals(2, fixture.platform().facts.size());
        assertEquals("ROUTER", fixture.deviceRows().getFirst().getDeviceTypeCodeSnapshot());
        assertEquals("pt-v1", fixture.deviceRows().getFirst().getDeviceTypeSourceVersionSnapshot());
        assertTrue(fixture.assessment().get().getContextSnapshot().contains("\"productTypeCode\":\"ROUTER\""));
        assertTrue(fixture.assessment().get().getContextSnapshot().contains("\"sourceVersion\":\"pt-v1\""));
        assertFalse(fixture.assessment().get().getContextSnapshot().contains("syncStatus"));
        assertFalse(fixture.assessment().get().getContextSnapshot().contains("lastSuccessfulSyncTime"));
        verify(fixture.productType(), times(2)).resolveAuthorized(8L, List.of(400L));
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
        DirectPlatform platform = new DirectPlatform();
        AtomicReference<CutoverTaskDO> task = new AtomicReference<>();
        AtomicReference<CutoverAssessmentDO> assessment = new AtomicReference<>();
        List<CutoverTaskDeviceScopeDO> deviceRows = new ArrayList<>();
        List<CutoverTaskStageHistoryDO> history = new ArrayList<>();

        CutoverProjectScopePort.ProjectScopeFact scopeFact =
                new CutoverProjectScopePort.ProjectScopeFact(100L, 7L, true);
        CutoverProjectContextPort.ProjectContextFact projectFact =
                new CutoverProjectContextPort.ProjectContextFact(1L, 100L, 3,
                        "PROJ-100", "核心网割接项目", 200L, "CUS-200", "示例客户",
                        300L, "OFFICE-300", "一号办事处", 7L);
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
        when(projectScope.lockAndRevalidate(8L, 100L, "ACTION_EDIT", 7L)).thenReturn(scopeFact);
        when(projectContext.inspect(1L, 100L, 7L)).thenReturn(projectFact);
        when(projectContext.lockAndRevalidate(projectFact)).thenReturn(projectFact);
        when(deviceScope.resolveBySerials(List.of("SN-400"))).thenReturn(devices);
        when(deviceScope.lockAndRevalidate(100L, devices)).thenReturn(devices);
        when(productType.resolveAuthorized(8L, List.of(400L))).thenReturn(productTypes);
        when(customerLevel.inspect(200L)).thenReturn(customerFact);
        when(customerLevel.lockAndRevalidate(customerFact)).thenReturn(customerFact);
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
            assessment.get().setManualGrade(update.manualGrade());
            assessment.get().setSimpleFlow(update.simpleFlow());
            return 1;
        });
        when(taskMapper.transitionIfMatch(any())).thenReturn(1);

        CutoverTaskApplicationService service = new CutoverTaskApplicationService(taskMapper, deviceMapper,
                historyMapper, assessmentMapper, configurationMapper, projectScope, projectContext,
                deviceScope, productType, customerLevel,
                readiness, platform, Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC));
        return new Fixture(service, task, assessment, deviceRows, history, platform, productType);
    }

    private static CreateCutoverTaskCommand createCommand(String key) {
        return new CreateCutoverTaskCommand(1L, 8L, key, "corr-" + key, "SELF_CREATED", 100L,
                List.of("SN-400"), "CUTOVER-V1", "核心网割接", "计划内设备割接", "配置变更", "普通双机",
                LocalDateTime.of(2026, 9, 1, 1, 0), null, null, null,
                new CreateCutoverTaskCommand.ExpectedCreateContext(
                        new CutoverProjectContextPort.ProjectContextFact(1L, 100L, 3,
                                "PROJ-100", "核心网割接项目", 200L, "CUS-200", "示例客户",
                                300L, "OFFICE-300", "一号办事处", 7L),
                        List.of(new CutoverDeviceScopePort.DeviceFact(400L, "SN-400", 100L, 9L)),
                        new CutoverCustomerLevelPort.CustomerLevelFact("AVAILABLE", 200L, "CUS-200", "示例客户",
                                500L, "LEVEL_1", 2L, LocalDateTime.of(2026, 8, 1, 0, 0), null),
                        new CutoverReadinessPort.ReadinessFact(600L, 4L, "READY", 100L,
                                List.of(400L), "watermark-1", List.of())));
    }

    private static SaveCutoverAssessmentCommand saveCommand(Long taskId, Integer taskVersion, String grade) {
        return new SaveCutoverAssessmentCommand(1L, 8L, taskId, taskVersion, 0,
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true), grade, "corr-save-" + grade);
    }

    private static SubmitCutoverAssessmentCommand submitCommand(Long taskId, Integer taskVersion,
                                                                 Integer assessmentVersion, String key) {
        return new SubmitCutoverAssessmentCommand(1L, 8L, taskId, taskVersion, assessmentVersion,
                key, "corr-" + key);
    }

    private record Fixture(CutoverTaskApplicationService service, AtomicReference<CutoverTaskDO> task,
                           AtomicReference<CutoverAssessmentDO> assessment,
                           List<CutoverTaskDeviceScopeDO> deviceRows,
                           List<CutoverTaskStageHistoryDO> history, DirectPlatform platform,
                           CutoverDeviceProductTypePort productType) {
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final List<SuccessFacts> facts = new ArrayList<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest, Class<T> responseType,
                                              Supplier<T> operation, Function<T, SuccessFacts> successFactsFactory) {
            T result = operation.get();
            facts.add(successFactsFactory.apply(result));
            return new ExecutionResult<>(Decision.NEW, result);
        }
    }
}
