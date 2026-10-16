package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalInspectResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.InspectStatus;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverPlanQueryServiceTest {

    @Test
    void assemblesDraftStepsFromChildRowsAndProjectsAllowedActions() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        CutoverPlanSourcePort.SourceFacts facts = facts();
        CutoverPlanSourcePort source = new CutoverPlanControlledPorts.SourcePort(facts);
        CutoverTaskDO task = task(); CutoverPlanRevisionDO plan = plan(facts);
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(projectScope.inspect(8L, 70L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(projectScope.inspect(8L, 70L, "ACTION_EDIT"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(planMapper.selectCurrent(any())).thenReturn(plan);
        when(stepMapper.selectListByPlan(any())).thenReturn(List.of(step(2L, "ROLLBACK", 1, "回退"),
                step(1L, "OPERATION", 1, "割接")));
        when(supportMapper.selectListByPlan(any())).thenReturn(List.of(support()));
        CutoverPlanQueryService service = new CutoverPlanQueryService(taskMapper, planMapper, stepMapper,
                supportMapper, projectScope, source, null, new CutoverPlanContentCodec());

        injectStorage(service, plan, facts().snapshot());
        CutoverPlanView view = service.detail(new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "query-test", true, true, true, true), 50L);

        assertThat(view.allowedActions()).containsExactly("SAVE_DRAFT", "DOWNLOAD_DRAFT");
        assertThat(view.content().path("steps")).hasSize(2);
        assertThat(view.content().path("steps").get(0).path("sectionCode").asText()).isEqualTo("OPERATION");
        assertThat(view.content().path("supportArrangements")).hasSize(1);
        assertThat(view.content().path("supportArrangements").get(0).path("roleCode").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void reopensPersistedGeneratedFileAndClearsItAfterNewContentSave() {
        Fixture fixture = fixture();
        var file = cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFact.builder()
                .artifactId(2110704555528138754L).versionNo(1).referenceKey("generated-test")
                .scopeVersion(1L).sha256("a".repeat(64))
                .fileFactVersion(new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFactVersion(1, 1, 1)).build();
        var generated = mock(CutoverPlanGeneratedFiles.class);
        when(generated.find(any())).thenAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanGeneratedFileQuery) call.getArgument(0);
            return query.planVersion().equals(1L) ? new cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult(80L, 1L, file, 1792066155900L) : null;
        });
        var service = fixture.service(mock(CutoverApprovalFactApi.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "generatedFiles", generated);
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "reopen-test", true, true, true, true);
        var reopened = service.detail(context, 50L);
        assertThat(reopened.generatedDraft().fileArtifactFact()).isEqualTo(file);
        assertThat(reopened.generatedDraft().planVersion()).isEqualTo(1L);
        fixture.plan.setVersion(2L);
        assertThat(service.detail(context, 50L).generatedDraft()).isNull();
    }

    @Test
    void projectsSubmitForCompleteDraftWhenApprovalContractIsAvailable() {
        Fixture fixture = fixture();
        fixture.plan.setEditModeCode("FULL_FILE_UPLOAD");
        fixture.plan.setFileArtifactId(501L); fixture.plan.setFileVersionNo(1);
        fixture.plan.setFileReferenceKey("plan-ref-1");
        fixture.plan.setFileFactVersion("{\"artifactVersion\":1,\"referenceVersion\":1,\"availabilityVersion\":1}");
        fixture.plan.setFileArtifactFactVersion(1L);
        fixture.plan.setFileReferenceFactVersion(1L);
        fixture.plan.setFileAvailabilityFactVersion(1L);
        fixture.plan.setFileScopeVersion(1L);
        fixture.plan.setFileSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        fixture.plan.setOwnershipConfirmed(true);
        fixture.plan.setScheduledTime(java.time.LocalDateTime.of(2026, 11, 1, 10, 0));

        CutoverPlanView view = fixture.service(mock(CutoverApprovalFactApi.class)).detail(new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "query-test", true, true, true, true), 50L);

        assertThat(view.allowedActions()).contains("SUBMIT_PLAN");
    }

    @Test
    void projectsRejectedApprovalAndReviseAction() {
        Fixture fixture = fixture();
        fixture.plan.setStatusCode("SUBMITTED"); fixture.plan.setApprovalInstanceId(901L);
        fixture.plan.setApprovalVersion(2L);
        CutoverApprovalFact fact = approval(fixture.plan, ApprovalStatus.REJECTED, 2, 1788220800000L, "请修订");
        CutoverApprovalFactApi approval = mock(CutoverApprovalFactApi.class);
        when(approval.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND, fact));

        CutoverPlanView view = fixture.service(approval).detail(new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "query-test", true, true, true, true), 50L);

        assertThat(view.allowedActions()).containsExactly("REVISE_PLAN");
        assertThat(view.approvalFact().status()).isEqualTo("REJECTED");
        assertThat(view.approvalFact().rejectionReason()).isEqualTo("请修订");
    }

    @Test
    void projectsSourceReplacementRevisionFromInvalidatedHistory() {
        Fixture fixture = fixture();
        fixture.plan.setStatusCode("INVALIDATED"); fixture.plan.setCurrentMarker(null);
        fixture.plan.setApprovalInstanceId(902L); fixture.plan.setApprovalVersion(3L);
        when(fixture.planMapper.selectCurrent(any())).thenReturn(null);
        when(fixture.planMapper.selectListHistory(any())).thenReturn(List.of(fixture.plan));
        when(fixture.planMapper.selectListDirectSuccessors(any())).thenReturn(List.of());
        CutoverApprovalFact fact = approval(fixture.plan, ApprovalStatus.PAUSED_SOURCE_INVALIDATED, 3, null, null);
        CutoverApprovalFactApi approval = mock(CutoverApprovalFactApi.class);
        when(approval.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND, fact));
        fixture.source = new CutoverPlanControlledPorts.SourcePort(changedFacts());

        CutoverPlanView view = fixture.service(approval).detail(new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "query-test", true, true, true, true), 50L);

        assertThat(view.planRevisionId()).isEqualTo(80L);
        assertThat(view.allowedActions()).containsExactly("REVISE_PLAN");
    }

    @Test
    void projectsApprovedContactUpdateAtP6() {
        Fixture fixture = fixture();
        fixture.task.setCurrentStage("P6"); fixture.task.setTaskStatus("CLOSURE_IN_PROGRESS");
        fixture.plan.setStatusCode("SUBMITTED"); fixture.plan.setApprovalInstanceId(903L);
        fixture.plan.setApprovalVersion(4L);
        CutoverApprovalFact fact = approval(fixture.plan, ApprovalStatus.APPROVED, 4, 1788220800000L, null);
        CutoverApprovalFactApi approval = mock(CutoverApprovalFactApi.class);
        when(approval.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND, fact));

        CutoverPlanView view = fixture.service(approval).detail(new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "query-test", true, true, true, true), 50L);

        assertThat(view.allowedActions()).containsExactly("UPDATE_APPROVED_CONTACTS");
        assertThat(view.approvalFact().status()).isEqualTo("APPROVED");
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static CutoverApprovalFact approval(CutoverPlanRevisionDO plan, ApprovalStatus status,
                                                 long version, Long decisionAt, String rejectionReason) {
        return new CutoverApprovalFact(plan.getApprovalInstanceId(), version, plan.getCutoverTaskId(), plan.getId(),
                plan.getRevisionNo(), status, 1, null, decisionAt, rejectionReason);
    }

    private static CutoverPlanSourcePort.SourceFacts changedFacts() {
        CutoverPlanSourcePort.SourceSnapshot source = facts().snapshot();
        CutoverPlanSourcePort.SourceSnapshot changed = new CutoverPlanSourcePort.SourceSnapshot(
                source.snapshotVersion() + 1, source.taskId(), source.taskVersion() + 1,
                source.assessmentId(), source.assessmentVersion(), source.grade(), source.checklistId(),
                source.checklistVersion(), source.projectId(), source.projectVersion(), source.projectScopeVersion(),
                source.devices(), source.configurationRevisionId(), source.configurationCode(),
                source.configurationRevisionNo(), source.templateSections(), source.failedRiskFacts());
        return new CutoverPlanSourcePort.SourceFacts(changed, changed.failedRiskFacts());
    }

    private static final class Fixture {
        private final CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        private final CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        private final CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        private final CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        private final CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        private final CutoverTaskDO task = task();
        private final CutoverPlanRevisionDO plan = plan(facts());
        private CutoverPlanSourcePort source = new CutoverPlanControlledPorts.SourcePort(facts());

        private Fixture() {
            when(taskMapper.selectById(50L)).thenReturn(task);
            when(projectScope.inspect(8L, 70L, "ACTION_VIEW"))
                    .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
            when(projectScope.inspect(8L, 70L, "ACTION_EDIT"))
                    .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
            when(planMapper.selectCurrent(any())).thenReturn(plan);
            when(planMapper.selectListDirectSuccessors(any())).thenReturn(List.of());
            when(stepMapper.selectListByPlan(any())).thenReturn(List.of());
            when(supportMapper.selectListByPlan(any())).thenReturn(List.of());
        }

        private CutoverPlanQueryService service(CutoverApprovalFactApi approval) {
            var service = new CutoverPlanQueryService(taskMapper, planMapper, stepMapper, supportMapper,
                    projectScope, source, approval, new CutoverPlanContentCodec());
            injectStorage(service, plan, facts().snapshot()); return service;
        }
    }

    private static void injectStorage(CutoverPlanQueryService service, CutoverPlanRevisionDO plan,
                                      CutoverPlanSourcePort.SourceSnapshot source) {
        var storage = mock(CutoverPlanStorage.class);
        var body = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(
                "{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\",\"overview\":{},\"riskMitigations\":[]}");
        when(storage.source(plan)).thenReturn(source); when(storage.content(plan)).thenAnswer(ignored -> body.deepCopy());
        when(storage.currentSupport(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.sourceView(plan)).thenReturn(JsonUtils.parseTree(JsonUtils.toJsonString(source)));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "storage", storage);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "generatedFiles", mock(CutoverPlanGeneratedFiles.class));
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(50L); row.setTenantId(1L); row.setProjectId(70L);
        row.setOwnerUserId(8L); row.setTaskOrigin(CutoverTaskRules.ORIGIN_NEW_PLATFORM);
        row.setCurrentStage(CutoverTaskRules.STAGE_P4); row.setTaskStatus(CutoverTaskRules.STATUS_PLAN_DRAFTING);
        row.setVersion(4L); return row;
    }

    private static CutoverPlanRevisionDO plan(CutoverPlanSourcePort.SourceFacts facts) {
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO(); row.setId(80L); row.setTenantId(1L);
        row.setCutoverTaskId(50L); row.setRevisionNo(1); row.setOriginCode("NEW_PLATFORM");
        row.setEditModeCode("ONLINE_TEMPLATE_STANDARD"); row.setStatusCode("DRAFT"); row.setCurrentMarker(1);
        row.setVersion(1L);
        return row;
    }

    private static CutoverPlanStepDO step(Long id, String section, int no, String content) {
        CutoverPlanStepDO row = new CutoverPlanStepDO(); row.setId(id); row.setSectionCode(section);
        row.setStepNo(no); row.setContent(content); return row;
    }

    private static CutoverSupportArrangementDO support() {
        CutoverSupportArrangementDO row = new CutoverSupportArrangementDO(); row.setId(91L);
        row.setRoleCode("CUSTOMER"); row.setPersonName("客户经理"); row.setDutyDescription("现场确认");
        row.setPhone("13800000000"); row.setArrivalTime(java.time.LocalDateTime.of(2026, 9, 1, 9, 0)); return row;
    }

    private static CutoverPlanSourcePort.SourceFacts facts() {
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> templates = new ArrayList<>();
        for (int i = 0; i < CutoverPlanRules.STANDARD_SECTIONS.size(); i++) templates.add(
                new CutoverPlanSourcePort.TemplateSectionSnapshot(CutoverPlanRules.STANDARD_SECTIONS.get(i),
                        CutoverPlanRules.STANDARD_SECTIONS.get(i), i + 1, List.of("NETWORK_CUTOVER"), List.of("A"), true));
        CutoverPlanSourcePort.SourceSnapshot snapshot = new CutoverPlanSourcePort.SourceSnapshot(1, 50L, 4L,
                100L, 2L, "A", 200L, 3L, 70L, 6, 30L,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(301L, "SN-1", 9L, "ROUTER", "type-v1")),
                401L, "CFG-1", 1, templates, List.of());
        return new CutoverPlanSourcePort.SourceFacts(snapshot, List.of());
    }
}
