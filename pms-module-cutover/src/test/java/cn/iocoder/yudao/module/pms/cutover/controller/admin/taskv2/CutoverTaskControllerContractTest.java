package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.CutoverTaskReqVO;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CutoverTaskControllerContractTest {

    @Test
    void keepsSixRouteCandidateOutsideProductionRegistration() {
        RequestMapping root = CutoverTaskController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/api/v1/pms/cutover-tasks");
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverTaskController.class, RestController.class)).isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverTaskController.class, Component.class)).isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverTaskController.class, ResponseBody.class)).isTrue();
    }

    @Test
    void projectsCreateContextAndCarriesFrozenFactsIntoSelfCreatedCommand() {
        CutoverTaskApplicationService applicationService = mock(CutoverTaskApplicationService.class);
        CutoverTaskQueryService queryService = mock(CutoverTaskQueryService.class);
        CutoverTaskRequestContext context = () -> new CutoverTaskRequestContext.TrustedContext(
                9L, 12L, "corr-1", true, true, true, true, true);
        CutoverTaskController controller = new CutoverTaskController(applicationService, queryService, context);
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 1, 30);
        var project = new CutoverProjectContextPort.ProjectContextFact(
                9L, 31L, 3, "P-001", "核心网扩容", 41L, "CUS-01", "示例客户",
                51L, "OFF-01", "华东办事处", 7L);
        var device = new CutoverDeviceScopePort.DeviceFact(61L, "SN-001", 31L, 8L);
        var customer = new CutoverCustomerLevelPort.CustomerLevelFact(
                "AVAILABLE", 41L, "CUS-01", "示例客户", 71L, "GOLD", 9L, now.minusDays(1), null);
        var readiness = new CutoverReadinessPort.ReadinessFact(
                81L, 10L, "READY", 31L, List.of(61L), "wm-1", List.of());
        when(queryService.resolveCreateContext(9L, 12L, List.of("SN-001"))).thenReturn(
                new CutoverTaskViews.CreateContextData(
                        List.of(new CutoverTaskViews.CreateContextCandidate(
                                project, List.of(device), customer, readiness, true)), false,
                        List.of(new CutoverTaskViews.ConfigurationChoice(
                                "CORE_STANDARD", "核心网标准割接", 91L, 2, now.minusDays(1), null)), false));

        var response = controller.resolveCreateContext(new CutoverTaskReqVO.ResolveCreateContext(List.of("SN-001")));
        assertThat(response.getData().candidates().getFirst().project()).satisfies(projectView -> {
            assertThat(projectView.officeDepartmentId()).isEqualTo(51L);
            assertThat(projectView.officeCode()).isEqualTo("OFF-01");
            assertThat(projectView.officeName()).isEqualTo("华东办事处");
        });
        assertThat(response.getData().configurationChoices()).extracting(
                CutoverTaskViews.ConfigurationChoice::configurationCode).containsExactly("CORE_STANDARD");

        when(applicationService.create(any())).thenReturn(
                new CutoverTaskCommandResult(101L, "CUT-001", "P2", "GRADE_CONFIRMING", 1, false));
        controller.create("intent-1", new CutoverTaskReqVO.Create(
                31L, "CORE_STANDARD", List.of("SN-001"), "核心网割接", "设备替换",
                "CORE_REPLACEMENT", "DUAL_PLANE", now,
                new CutoverTaskReqVO.ProjectContext(31L, 3, "P-001", "核心网扩容",
                        41L, "CUS-01", "示例客户", 51L, "OFF-01", "华东办事处"),
                7L, List.of(new CutoverTaskReqVO.DeviceWatermark(61L, "SN-001", 8L)),
                81L, 10L, "AVAILABLE", 71L, "GOLD", 9L, now.minusDays(1), null));

        ArgumentCaptor<CreateCutoverTaskCommand> command = ArgumentCaptor.forClass(CreateCutoverTaskCommand.class);
        verify(applicationService).create(command.capture());
        assertThat(command.getValue()).satisfies(value -> {
            assertThat(value.tenantId()).isEqualTo(9L);
            assertThat(value.actorId()).isEqualTo(12L);
            assertThat(value.configurationCode()).isEqualTo("CORE_STANDARD");
            assertThat(value.idempotencyKey()).isEqualTo("intent-1");
            assertThat(value.correlationId()).isEqualTo("corr-1");
            assertThat(value.expectedContext().project()).isEqualTo(project);
            assertThat(value.expectedContext().devices()).containsExactly(device);
            assertThat(value.expectedContext().customer()).isEqualTo(customer);
            assertThat(value.expectedContext().readiness()).satisfies(fact -> {
                assertThat(fact.snapshotId()).isEqualTo(81L);
                assertThat(fact.snapshotVersion()).isEqualTo(10L);
                assertThat(fact.decision()).isEqualTo("READY");
                assertThat(fact.deviceIds()).containsExactly(61L);
            });
        });
    }
}
