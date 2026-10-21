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
    void exportsSubmittedOnlinePlanWithoutChangingVersionsOrFollowingContactAdjustments() throws Exception {
        var f = fixture();
        f.source = mock(CutoverPlanSourcePort.class);
        f.task.setCurrentStage("P5"); f.task.setTaskStatus("APPROVING"); f.task.setTaskName("提交后下载验收");
        f.plan.setStatusCode("SUBMITTED"); f.plan.setGradeCode("A");
        when(f.planMapper.selectById(80L)).thenReturn(f.plan);
        when(f.stepMapper.selectListByPlan(any())).thenReturn(List.of(step(90L, "OPERATION", 1, "原提交操作")));
        when(f.supportMapper.selectListByPlan(any())).thenReturn(List.of(support()));
        var service = f.service(mock(CutoverApprovalFactApi.class));
        var store = (CutoverPlanStorage) org.springframework.test.util.ReflectionTestUtils.getField(service, "storage");
        var adjusted = support(); adjusted.setPersonName("后续改为的联系人");
        when(store.currentSupport(any())).thenReturn(adjusted);
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "submitted-download", false, false, false, true);
        var result = service.downloadSubmitted(new CutoverPlanQueryService.SubmittedDownloadQuery(context, 50L, 1L));
        assertThat(result.fileName()).isEqualTo("割接方案-v1.docx");
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(result.content()))) {
            String text = document.getParagraphs().stream().map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText).collect(java.util.stream.Collectors.joining("\n"))
                    + document.getTables().stream().map(org.apache.poi.xwpf.usermodel.XWPFTable::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(text).contains("原提交操作", "客户经理").doesNotContain("后续改为的联系人");
        }
        assertThat(f.plan.getVersion()).isEqualTo(1L);
        assertThat(f.plan.getStatusCode()).isEqualTo("SUBMITTED");
        verify(f.planMapper, never()).replaceDraftIfMatch(any());
        verify(f.planMapper, never()).submitDraftIfMatch(any());
        verify(f.source, never()).inspect(any(), any(), any());
    }

    @Test
    void submittedDownloadRejectsMissingPermissionInvisibleScopeStaleVersionAndWrongState() {
        for (String change : List.of("permission", "scope", "version", "state", "tenant")) {
            var f = fixture(); f.task.setCurrentStage("P5"); f.task.setTaskStatus("APPROVING");
            f.plan.setStatusCode("SUBMITTED");
            when(f.planMapper.selectById(80L)).thenReturn(f.plan);
            var service = f.service(mock(CutoverApprovalFactApi.class));
            org.springframework.test.util.ReflectionTestUtils.setField(service, "approvalAudience",
                    mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience.class));
            if (change.equals("scope")) when(f.projectScope.inspect(8L, 70L, "ACTION_VIEW")).thenReturn(null);
            if (change.equals("state")) f.plan.setStatusCode("DRAFT");
            if (change.equals("tenant")) f.task.setTenantId(2L);
            var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "download-denied", false, false, false, !change.equals("permission"));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.downloadSubmitted(
                    new CutoverPlanQueryService.SubmittedDownloadQuery(context, 50L, change.equals("version") ? 2L : 1L)))
                    .as(change).isInstanceOfAny(CutoverPlanApplicationException.class, org.springframework.security.access.AccessDeniedException.class);
            verify(f.planMapper, never()).replaceDraftIfMatch(any());
        }
    }

    @Test
    void submittedExportDoesNotExposeAttachmentNamesFromAnotherTask() {
        var f = fixture(); f.task.setCurrentStage("P5"); f.task.setTaskStatus("APPROVING"); f.plan.setStatusCode("SUBMITTED");
        when(f.planMapper.selectById(80L)).thenReturn(f.plan);
        var service = f.service(mock(CutoverApprovalFactApi.class));
        var store = (CutoverPlanStorage) org.springframework.test.util.ReflectionTestUtils.getField(service, "storage");
        var body = (tools.jackson.databind.node.ObjectNode) store.content(f.plan);
        body.withObject("/overview").set("preTopologyFile", JsonUtils.parseTree("{\"artifactId\":\"901\",\"versionNo\":1,\"referenceKey\":\"topology\"}"));
        when(store.content(f.plan)).thenAnswer(ignored -> body.deepCopy());
        var attachments = mock(CutoverPlanDocumentAttachments.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "documentAttachments", attachments);
        when(attachments.resolve(any())).thenThrow(new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.FILE_FACT_STALE, "方案引用附件版本不可用"));
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "file-scope", false, false, false, true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.downloadSubmitted(
                new CutoverPlanQueryService.SubmittedDownloadQuery(context, 50L, 1L)))
                .isInstanceOf(CutoverPlanApplicationException.class).hasMessage("方案引用附件版本不可用");
    }

    @Test
    void approvalTaskReadDoesNotGrantPlanSaveSubmitReviseOrContactWrites() {
        var f = fixture();
        when(f.projectScope.inspect(8L, 70L, "ACTION_VIEW")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, false));
        var service = f.service(mock(CutoverApprovalFactApi.class));
        var audience = mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "approvalAudience", audience);
        when(audience.inspectTask(any())).thenReturn(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience.Grant(20L, "{}"));
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "approval-read", true, true, true, true);
        var result = service.detail(context, 50L);
        assertThat(result.planRevisionId()).isEqualTo(f.plan.getId());
        assertThat(result.allowedActions()).isEmpty();
        verify(f.projectScope, never()).inspect(8L, 70L, "ACTION_EDIT");
        when(audience.inspectTask(any())).thenReturn(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.detail(context, 50L)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void permitsOnlyDraftSaveWhenPrecedingSourceIsUnavailableAndKeepsAccessBoundaries() {
        Fixture fixture = fixture();
        fixture.source = mock(CutoverPlanSourcePort.class);
        when(fixture.source.inspect(any(), any(), any())).thenThrow(new IllegalStateException("source incomplete"));
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "draft", true, true, true, true);
        var service = fixture.service(mock(CutoverApprovalFactApi.class));
        assertThat(service.detail(context, 50L).allowedActions()).contains("SAVE_DRAFT").doesNotContain("SUBMIT_PLAN");
        when(fixture.projectScope.inspect(8L, 70L, "ACTION_EDIT"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, false));
        assertThat(service.detail(context, 50L).allowedActions()).doesNotContain("SAVE_DRAFT", "SUBMIT_PLAN");
        when(fixture.projectScope.inspect(8L, 70L, "ACTION_EDIT"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        fixture.plan.setStatusCode("SUBMITTED");
        assertThat(service.detail(context, 50L).allowedActions()).doesNotContain("SAVE_DRAFT");
    }

    @Test
    void projectAuditVersionDoesNotHideChangesToProtectedSourceFacts() {
        var original = facts().snapshot();
        for (String field : java.util.List.of("projectId", "projectScopeVersion", "taskVersion", "assessmentVersion", "configurationRevisionId")) {
            var tree = (tools.jackson.databind.node.ObjectNode) JsonUtils.getObjectMapper().valueToTree(original);
            tree.put("projectVersion", original.projectVersion() + 1);
            tree.put(field, tree.path(field).asLong() + 1);
            var changed = JsonUtils.getObjectMapper().treeToValue(tree, CutoverPlanSourcePort.SourceSnapshot.class);
            assertThat(original.sameBusinessSource(changed)).as(field).isFalse();
        }
        var tree = (tools.jackson.databind.node.ObjectNode) JsonUtils.getObjectMapper().valueToTree(original);
        ((tools.jackson.databind.node.ObjectNode) tree.path("devices").get(0)).put("projectAssignmentVersion", 99L);
        assertThat(original.sameBusinessSource(JsonUtils.getObjectMapper().treeToValue(tree,
                CutoverPlanSourcePort.SourceSnapshot.class))).isFalse();
    }

    @Test
    void keepsDraftActionsWhenOnlyGeneralProjectVersionChanged() {
        Fixture fixture = fixture();
        var tree = (tools.jackson.databind.node.ObjectNode) JsonUtils.getObjectMapper().valueToTree(facts().snapshot());
        tree.put("projectVersion", facts().snapshot().projectVersion()+1);
        var snapshot = JsonUtils.getObjectMapper().treeToValue(tree,CutoverPlanSourcePort.SourceSnapshot.class);
        fixture.source = new CutoverPlanControlledPorts.SourcePort(new CutoverPlanSourcePort.SourceFacts(snapshot,snapshot.failedRiskFacts()));
        var view = fixture.service(mock(CutoverApprovalFactApi.class)).detail(
                new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L,8L,"refresh",true,true,true,true),50L);
        assertThat(view.allowedActions()).contains("SAVE_DRAFT").doesNotContain("REFRESH_DRAFT_SOURCE");
        assertThat(view.sourceSnapshot().path("projectVersion").asInt()).isEqualTo(facts().snapshot().projectVersion());
    }

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
        var submitOnly = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "submit-only", false, false, true, false);
        assertThat(fixture.service(mock(CutoverApprovalFactApi.class)).hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(submitOnly, 50L))).isTrue();
        fixture.source = mock(CutoverPlanSourcePort.class);
        when(fixture.source.inspect(any(), any(), any())).thenReturn(changedFacts());
        assertThat(fixture.service(mock(CutoverApprovalFactApi.class)).hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(submitOnly, 50L))).isFalse();
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
        var revisionContext = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "revise-action", true, true, true, true);
        assertThat(fixture.service(approval).hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(revisionContext, 50L))).isTrue();
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
        var revisionContext = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "revise-action", true, true, true, true);
        assertThat(fixture.service(approval).hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(revisionContext, 50L))).isTrue();
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

    @Test
    void workbenchSaveDoesNotLoadSourceCompletenessOrDisplayChildren() {
        var f = fixture();
        f.source = mock(CutoverPlanSourcePort.class);
        var service = f.service(mock(CutoverApprovalFactApi.class));
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "workbench", true, true, true, true);
        assertThat(service.hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(context, 50L))).isTrue();
        verifyNoInteractions(f.source, f.stepMapper, f.supportMapper);
        verify(f.projectScope).inspect(8L, 70L, "ACTION_VIEW");
        verify(f.projectScope).inspect(8L, 70L, "ACTION_EDIT");
        var generated = (CutoverPlanGeneratedFiles) org.springframework.test.util.ReflectionTestUtils.getField(service, "generatedFiles");
        verifyNoInteractions(generated);
    }

    @Test
    void workbenchMembershipMatchesDetailAcrossPermissionsStatusesAndOwnership() {
        var operations = java.util.Set.of("CREATE_DRAFT", "SAVE_DRAFT", "SUBMIT_PLAN", "REVISE_PLAN");
        for (String status : List.of("NONE", "DRAFT", "SUBMITTED", "INVALIDATED")) {
            for (int permissions = 0; permissions < 8; permissions++) {
                for (boolean owner : List.of(true, false)) {
                    var f = fixture();
                    f.task.setOwnerUserId(owner ? 8L : 99L);
                    if ("NONE".equals(status)) when(f.planMapper.selectCurrent(any())).thenReturn(null);
                    else {
                        f.plan.setStatusCode(status);
                        if ("INVALIDATED".equals(status)) { f.plan.setCurrentMarker(null); f.plan.setInvalidationReasonCode("USER_REVISION"); }
                    }
                    var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "equivalence", (permissions & 1) != 0, (permissions & 2) != 0, (permissions & 4) != 0, true);
                    var service = f.service(mock(CutoverApprovalFactApi.class));
                    boolean expected = service.detail(context, 50L).allowedActions().stream().anyMatch(operations::contains);
                    assertThat(service.hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(context, 50L)))
                            .as("status=%s permissions=%s owner=%s", status, permissions, owner).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void workbenchRetainsTenantAndProjectAuthorizationAndEditScope() {
        var f = fixture();
        var service = f.service(mock(CutoverApprovalFactApi.class));
        var audience = mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "approvalAudience", audience);
        var context = new cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext(1L, 8L, "denied", true, true, true, true);
        f.task.setTenantId(2L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(context, 50L)))
                .isInstanceOf(CutoverPlanApplicationException.class);
        f.task.setTenantId(1L);
        when(f.projectScope.inspect(8L, 70L, "ACTION_VIEW")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(999L, 30L, true));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(context, 50L)))
                .isInstanceOf(CutoverPlanApplicationException.class);
        when(audience.inspectTask(any())).thenReturn(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience.Grant(90L, "{}"));
        assertThat(service.hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(context, 50L))).isFalse();
        when(f.projectScope.inspect(8L, 70L, "ACTION_VIEW")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(f.projectScope.inspect(8L, 70L, "ACTION_EDIT")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, false));
        assertThat(service.hasWorkbenchAction(new CutoverPlanQueryService.ActionQuery(context, 50L))).isFalse();
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
