package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.DownloadCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverPlanSubmissionTest {

    @Test
    void downloadsCompleteSimpleDraftWithoutAdvancingTaskOrPlan() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        CutoverPlanFilePort filePort = mock(CutoverPlanFilePort.class);
        CutoverTaskDO task = task();
        CutoverPlanRevisionDO plan = simplePlan();
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(projectScope.inspect(8L, 70L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(planMapper.selectCurrent(any())).thenReturn(plan);
        when(stepMapper.selectListByPlan(any())).thenReturn(List.of(
                step("OPERATION", 1, "执行割接"), step("ROLLBACK", 1, "执行回退")));
        CutoverPlanFilePort.FileFact generated = fileFact();
        when(filePort.downloadDraft(1L, 8L, 70L, 80L)).thenReturn(generated);
        CutoverPlanApplicationService service = new CutoverPlanApplicationService(taskMapper, planMapper,
                stepMapper, supportMapper, projectScope, mock(CutoverPlanSourcePort.class), filePort,
                new CutoverPlanContentCodec(), new DirectPlatform(),
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC));

        DownloadCutoverPlanDraftResult result = service.downloadDraft(
                new DownloadCutoverPlanDraftCommand(1L, 8L, 50L, 3, "download-1", "corr-download-1"));

        assertThat(result.planRevisionId()).isEqualTo(80L);
        assertThat(result.planVersion()).isEqualTo(3);
        assertThat(result.fileArtifactFact()).isEqualTo(generated);
        assertThat(result.downloadedAt()).isEqualTo(Instant.parse("2026-09-01T01:00:00Z").toEpochMilli());
        assertThat(task.getVersion()).isEqualTo(4);
        assertThat(plan.getVersion()).isEqualTo(3);
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO();
        row.setId(50L); row.setTenantId(1L); row.setProjectId(70L); row.setTaskOrigin("NEW_PLATFORM");
        row.setCurrentStage("P4"); row.setTaskStatus("PLAN_DRAFTING"); row.setVersion(4); return row;
    }

    private static CutoverPlanRevisionDO simplePlan() {
        CutoverPlanSourcePort.SourceSnapshot source = new CutoverPlanSourcePort.SourceSnapshot(1, 50L, 4,
                60L, 2, "D", null, null, 70L, 5, 30L,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(90L, "SN-90", 3L, "SWITCH", "TYPE-V1")),
                100L, "CFG-D", 1,
                List.of(new CutoverPlanSourcePort.TemplateSectionSnapshot("OPERATION", "操作", 1,
                                List.of("NETWORK_CUTOVER"), List.of("D"), true),
                        new CutoverPlanSourcePort.TemplateSectionSnapshot("ROLLBACK", "回退", 2,
                                List.of("NETWORK_CUTOVER"), List.of("D"), true)), List.of());
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO();
        row.setId(80L); row.setTenantId(1L); row.setCutoverTaskId(50L); row.setRevisionNo(1);
        row.setOriginCode("NEW_PLATFORM"); row.setEditModeCode("ONLINE_TEMPLATE_SIMPLE_D");
        row.setGradeCode("D"); row.setSourceSnapshot(JsonUtils.toJsonString(source));
        row.setContentSnapshot("{\"editMode\":\"ONLINE_TEMPLATE_SIMPLE_D\"}");
        row.setStatusCode("DRAFT"); row.setCurrentMarker(1); row.setVersion(3); return row;
    }

    private static CutoverPlanStepDO step(String section, int no, String content) {
        CutoverPlanStepDO row = new CutoverPlanStepDO();
        row.setSectionCode(section); row.setStepNo(no); row.setContent(content); return row;
    }

    private static CutoverPlanFilePort.FileFact fileFact() {
        return new CutoverPlanFilePort.FileFact(501L, 1, "cut-plan-draft-80",
                new CutoverPlanFilePort.FileFactVersion(1, 1, 1), 1L, "a".repeat(64));
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                              Class<T> responseType, Supplier<T> operation,
                                              Function<T, SuccessFacts> successFactsFactory) {
            T result = operation.get();
            SuccessFacts facts = successFactsFactory.apply(result);
            assertThat(facts.correlationId()).isEqualTo("corr-download-1");
            assertThat(facts.detailSnapshot()).contains("fileArtifactFact", "downloadedAt", "actorId");
            return new ExecutionResult<>(Decision.NEW, result);
        }
    }
}
