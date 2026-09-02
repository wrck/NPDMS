package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.checklist.CutoverChecklistReqVO;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RequestCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CollectionRequestCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistExportResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CutoverChecklistControllerContractTest {

    @Test
    void keepsCandidateOutsideProductionRegistration() {
        RequestMapping root = CutoverChecklistController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/api/v1/pms/cutover-tasks/{taskId}/checklist");
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverChecklistController.class, RestController.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverChecklistController.class, Component.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverChecklistController.class, ResponseBody.class))
                .isTrue();
    }

    @Test
    void mapsTrustedContextAndVersionsIntoGenerateAndSaveCommands() {
        CutoverChecklistApplicationService service = mock(CutoverChecklistApplicationService.class);
        CutoverChecklistExportService exportService = mock(CutoverChecklistExportService.class);
        CutoverChecklistRequestContext context = () ->
                new CutoverChecklistRequestContext.TrustedContext(9L, 12L, "corr-1");
        CutoverChecklistController controller = new CutoverChecklistController(service, exportService, context);
        when(service.generate(org.mockito.ArgumentMatchers.any())).thenReturn(
                new ChecklistCommandResult(31L, 41L, 1, 0, "DRAFT", "P3", 7, false));

        controller.generate(31L, "intent-1", new CutoverChecklistReqVO.Generate(
                7, 2, 19L, Map.of("risk-1", new CutoverChecklistReqVO.SelectedDefinition(71L, 3))));
        ArgumentCaptor<GenerateChecklistCommand> generate = ArgumentCaptor.forClass(GenerateChecklistCommand.class);
        verify(service).generate(generate.capture());
        assertThat(generate.getValue()).satisfies(command -> {
            assertThat(command.tenantId()).isEqualTo(9L);
            assertThat(command.actorId()).isEqualTo(12L);
            assertThat(command.taskId()).isEqualTo(31L);
            assertThat(command.expectedTaskVersion()).isEqualTo(7);
            assertThat(command.expectedAssessmentVersion()).isEqualTo(2);
            assertThat(command.expectedProjectScopeVersion()).isEqualTo(19L);
            assertThat(command.idempotencyKey()).isEqualTo("intent-1");
            assertThat(command.correlationId()).isEqualTo("corr-1");
        });

        controller.save(31L, new CutoverChecklistReqVO.Save(7, 19L, 41L, 1,
                List.of(new CutoverChecklistReqVO.DirectAnswer("risk-1", "checked"))));
        ArgumentCaptor<SaveChecklistCommand> save = ArgumentCaptor.forClass(SaveChecklistCommand.class);
        verify(service).save(save.capture());
        assertThat(save.getValue().answers()).containsExactly(
                new SaveChecklistCommand.DirectAnswer("risk-1", "checked"));

        when(service.requestCollection(org.mockito.ArgumentMatchers.any())).thenReturn(
                new CollectionRequestCommandResult(31L, 4, 41L, 1, 2,
                        51L, 0, "risk-1", 1, 61L, 71L, 1L,
                        "COMPLETED", null, true, false));
        controller.requestCollection(31L, "risk-1", "collect-intent", new CutoverChecklistReqVO.CollectionRequest(
                7, 19L, 41L, 1, 9007199254740991L, 9007199254740992L));
        ArgumentCaptor<RequestCollectionCommand> collection =
                ArgumentCaptor.forClass(RequestCollectionCommand.class);
        verify(service).requestCollection(collection.capture());
        assertThat(collection.getValue()).satisfies(command -> {
            assertThat(command.tenantId()).isEqualTo(9L);
            assertThat(command.actorId()).isEqualTo(12L);
            assertThat(command.deviceId()).isEqualTo(9007199254740991L);
            assertThat(command.commandTemplateId()).isEqualTo(9007199254740992L);
            assertThat(command.idempotencyKey()).isEqualTo("collect-intent");
            assertThat(command.correlationId()).isEqualTo("corr-1");
        });

        when(exportService.export(9L, 12L, 31L, 3, "corr-1"))
                .thenReturn(new CutoverChecklistExportResult(new byte[]{1, 2},
                        "cutover-checklist-31-v3.xlsx", 1, 0));
        var response = controller.export(31L, new CutoverChecklistReqVO.Export(3));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"cutover-checklist-31-v3.xlsx\"");
        assertThat(response.getBody()).containsExactly(1, 2);
    }
}
