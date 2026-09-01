package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalInspectResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.InspectStatus;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
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

        CutoverPlanView view = service.detail(1L, 8L, 50L,
                new CutoverPlanQueryService.PlanAccess(true, true, true, true));

        assertThat(view.allowedActions()).containsExactly("SAVE_DRAFT", "DOWNLOAD_DRAFT");
        assertThat(view.content().path("steps")).hasSize(2);
        assertThat(view.content().path("steps").get(0).path("sectionCode").asText()).isEqualTo("OPERATION");
        assertThat(view.content().path("supportArrangements")).hasSize(1);
        assertThat(view.content().path("supportArrangements").get(0).path("roleCode").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void readsLegacyPlanByImmutableLegacyIdentityWithNoActions() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        CutoverTaskDO task = task(); task.setTaskOrigin("LEGACY_FORWARD");
        task.setCurrentStage(null); task.setTaskStatus("LEGACY_UNKNOWN"); task.setOwnerUserId(null);
        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO(); plan.setId(81L); plan.setTenantId(1L);
        plan.setCutoverTaskId(50L); plan.setRevisionNo(1); plan.setOriginCode("LEGACY_FORWARD");
        plan.setStatusCode(null); plan.setCurrentMarker(null); plan.setLegacyPlanId(701L); plan.setLegacyStatusRaw(2);
        plan.setVersion(0); plan.setSourceSnapshot("""
                {"sourceTable":"pms_cut_plan","sourceId":701,"sourceTenantId":1,"sourceTaskId":50,
                 "sourceVersion":3,"sourceStatusRaw":2,"mappingVersion":"FCUT004_LEGACY_V1",
                 "code":"CUT-LEGACY-001","name":"旧割接方案","level":"A","remark":"历史备注"}
                """);
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(projectScope.inspect(8L, 70L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(planMapper.selectListLegacyByTask(any())).thenReturn(List.of(plan));
        when(stepMapper.selectListByPlan(any())).thenReturn(List.of(step(1L, "OPERATION", 1, "旧割接步骤")));
        CutoverPlanQueryService service = new CutoverPlanQueryService(taskMapper, planMapper, stepMapper,
                mock(CutoverSupportArrangementMapper.class), projectScope, mock(CutoverPlanSourcePort.class),
                null, new CutoverPlanContentCodec());

        CutoverPlanView view = service.detail(1L, 8L, 50L,
                new CutoverPlanQueryService.PlanAccess(true, true, true, true));

        assertThat(view.taskStage()).isNull();
        assertThat(view.originCode()).isEqualTo("LEGACY_FORWARD");
        assertThat(view.status()).isEqualTo("LEGACY_READ_ONLY");
        assertThat(view.legacyPlanId()).isEqualTo(701L);
        assertThat(view.legacyStatusRaw()).isEqualTo(2);
        assertThat(view.sourceSnapshot().path("sourceId").asLong()).isEqualTo(701L);
        assertThat(view.sourceSnapshot().path("mappingVersion").asText()).isEqualTo("FCUT004_LEGACY_V1");
        assertThat(view.content().path("editMode").asText()).isEqualTo("LEGACY_READ_ONLY");
        assertThat(view.allowedActions()).isEmpty();
        verify(planMapper, never()).selectCurrent(any());
        verify(projectScope, never()).inspect(8L, 70L, "ACTION_EDIT");
    }

    @Test
    void projectsSubmitForCompleteDraftWhenApprovalContractIsAvailable() {
        Fixture fixture = fixture();
        fixture.plan.setEditModeCode("FULL_FILE_UPLOAD");
        fixture.plan.setContentSnapshot(null);
        fixture.plan.setFileArtifactId(501L); fixture.plan.setFileVersionNo(1);
        fixture.plan.setFileReferenceKey("plan-ref-1");
        fixture.plan.setFileFactVersion("{\"artifactVersion\":1,\"referenceVersion\":1,\"availabilityVersion\":1}");
        fixture.plan.setFileScopeVersion(1L);
        fixture.plan.setFileSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        fixture.plan.setOwnershipConfirmed(true);

        CutoverPlanView view = fixture.service(mock(CutoverApprovalFactApi.class)).detail(1L, 8L, 50L,
                new CutoverPlanQueryService.PlanAccess(true, true, true, true));

        assertThat(view.allowedActions()).contains("SUBMIT_PLAN");
    }

    @Test
    void projectsRejectedApprovalAndReviseAction() {
        Fixture fixture = fixture();
        fixture.plan.setStatusCode("SUBMITTED"); fixture.plan.setApprovalInstanceId(901L);
        fixture.plan.setApprovalVersion(2);
        CutoverApprovalFact fact = approval(fixture.plan, ApprovalStatus.REJECTED, 2, 1788220800000L, "请修订");
        CutoverApprovalFactApi approval = mock(CutoverApprovalFactApi.class);
        when(approval.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND, fact));

        CutoverPlanView view = fixture.service(approval).detail(1L, 8L, 50L,
                new CutoverPlanQueryService.PlanAccess(true, true, true, true));

        assertThat(view.allowedActions()).containsExactly("REVISE_PLAN");
        assertThat(view.approvalFact().status()).isEqualTo("REJECTED");
        assertThat(view.approvalFact().rejectionReason()).isEqualTo("请修订");
    }

    @Test
    void projectsSourceReplacementRevisionFromInvalidatedHistory() {
        Fixture fixture = fixture();
        fixture.plan.setStatusCode("INVALIDATED"); fixture.plan.setCurrentMarker(null);
        fixture.plan.setApprovalInstanceId(902L); fixture.plan.setApprovalVersion(3);
        when(fixture.planMapper.selectCurrent(any())).thenReturn(null);
        when(fixture.planMapper.selectListHistory(any())).thenReturn(List.of(fixture.plan));
        when(fixture.planMapper.selectListDirectSuccessors(any())).thenReturn(List.of());
        CutoverApprovalFact fact = approval(fixture.plan, ApprovalStatus.PAUSED_SOURCE_INVALIDATED, 3, null, null);
        CutoverApprovalFactApi approval = mock(CutoverApprovalFactApi.class);
        when(approval.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND, fact));
        fixture.source = new CutoverPlanControlledPorts.SourcePort(changedFacts());

        CutoverPlanView view = fixture.service(approval).detail(1L, 8L, 50L,
                new CutoverPlanQueryService.PlanAccess(true, true, true, true));

        assertThat(view.planRevisionId()).isEqualTo(80L);
        assertThat(view.allowedActions()).containsExactly("REVISE_PLAN");
    }

    @Test
    void projectsApprovedContactUpdateAtP6() {
        Fixture fixture = fixture();
        fixture.task.setCurrentStage("P6"); fixture.task.setTaskStatus("CLOSURE_IN_PROGRESS");
        fixture.plan.setStatusCode("SUBMITTED"); fixture.plan.setApprovalInstanceId(903L);
        fixture.plan.setApprovalVersion(4);
        CutoverApprovalFact fact = approval(fixture.plan, ApprovalStatus.APPROVED, 4, 1788220800000L, null);
        CutoverApprovalFactApi approval = mock(CutoverApprovalFactApi.class);
        when(approval.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND, fact));

        CutoverPlanView view = fixture.service(approval).detail(1L, 8L, 50L,
                new CutoverPlanQueryService.PlanAccess(true, true, true, true));

        assertThat(view.allowedActions()).containsExactly("UPDATE_APPROVED_CONTACTS");
        assertThat(view.approvalFact().status()).isEqualTo("APPROVED");
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static CutoverApprovalFact approval(CutoverPlanRevisionDO plan, ApprovalStatus status,
                                                 int version, Long decisionAt, String rejectionReason) {
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
            return new CutoverPlanQueryService(taskMapper, planMapper, stepMapper, supportMapper,
                    projectScope, source, approval, new CutoverPlanContentCodec());
        }
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(50L); row.setTenantId(1L); row.setProjectId(70L);
        row.setOwnerUserId(8L); row.setTaskOrigin(CutoverTaskRules.ORIGIN_NEW_PLATFORM);
        row.setCurrentStage(CutoverTaskRules.STAGE_P4); row.setTaskStatus(CutoverTaskRules.STATUS_PLAN_DRAFTING);
        row.setVersion(4); return row;
    }

    private static CutoverPlanRevisionDO plan(CutoverPlanSourcePort.SourceFacts facts) {
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO(); row.setId(80L); row.setTenantId(1L);
        row.setCutoverTaskId(50L); row.setRevisionNo(1); row.setOriginCode("NEW_PLATFORM");
        row.setEditModeCode("ONLINE_TEMPLATE_STANDARD"); row.setStatusCode("DRAFT"); row.setCurrentMarker(1);
        row.setVersion(1); row.setSourceSnapshot(JsonUtils.toJsonString(facts.snapshot()));
        row.setContentSnapshot("{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\",\"overview\":{},\"riskMitigations\":[]}"); return row;
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
        CutoverPlanSourcePort.SourceSnapshot snapshot = new CutoverPlanSourcePort.SourceSnapshot(1, 50L, 4,
                100L, 2, "A", 200L, 3, 70L, 6, 30L,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(301L, "SN-1", 9L, "ROUTER", "type-v1")),
                401L, "CFG-1", 1, templates, List.of());
        return new CutoverPlanSourcePort.SourceFacts(snapshot, List.of());
    }
}
