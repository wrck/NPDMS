package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverClosureQueryServiceTest {

    @Test
    void returnsDraftDetailAndStableAllowedActions() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverApprovalInstanceMapper approvalMapper = mock(CutoverApprovalInstanceMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverClosureMapper closureMapper = mock(CutoverClosureMapper.class);
        CutoverClosureAttachmentMapper attachmentMapper = mock(CutoverClosureAttachmentMapper.class);
        CutoverCollectionEvidenceMapper evidenceMapper = mock(CutoverCollectionEvidenceMapper.class);
        CutoverTaskDO task = new CutoverTaskDO(); task.setId(100L); task.setTenantId(1L); task.setProjectId(10L);
        task.setTaskOrigin("NEW_PLATFORM"); task.setCurrentStage("P6"); task.setTaskStatus("CLOSURE_IN_PROGRESS");
        task.setOwnerUserId(9L); task.setVersion(7L);
        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO(); plan.setId(300L); plan.setCutoverTaskId(100L);
        plan.setRevisionNo(2); plan.setStatusCode("SUBMITTED"); plan.setApprovalInstanceId(200L);
        plan.setApprovalVersion(4L); plan.setVersion(6L);
        CutoverApprovalInstanceDO approval = new CutoverApprovalInstanceDO(); approval.setId(200L);
        approval.setTaskId(100L); approval.setPlanRevisionId(300L); approval.setStatusCode("APPROVED");
        approval.setVersion(4L); approval.setProjectId(10L); approval.setPlanRevisionNo(2);
        CutoverClosureDO closure = new CutoverClosureDO(); closure.setId(400L); closure.setTenantId(1L);
        closure.setTaskId(100L); closure.setProjectId(10L); closure.setTaskVersionAtP6(7L);
        closure.setPlanRevisionId(300L); closure.setPlanRevisionNo(2); closure.setPlanVersion(6L);
        closure.setApprovalInstanceId(200L); closure.setApprovalVersion(4L);
        closure.setStatusCode("DRAFT"); closure.setVersion(1L); closure.setPreCheckNormal(true);
        closure.setExecutionNormal(true); closure.setTestNormal(true); closure.setRollbackOccurred(false);
        closure.setFinalResultCode(null);
        CutoverClosureAttachmentDO checklist = attachment(1L, "POST_COLLECTION_CHECKLIST", "ref-check");
        CutoverClosureAttachmentDO commitment = attachment(2L, "IMPLEMENTATION_COMMITMENT", "ref-commit");
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(closureMapper.selectByTask(any())).thenReturn(closure);
        when(planMapper.selectById(300L)).thenReturn(plan);
        when(approvalMapper.selectById(200L)).thenReturn(approval);
        when(attachmentMapper.selectListByClosure(any())).thenReturn(List.of(commitment, checklist));
        when(evidenceMapper.selectListByClosure(any())).thenReturn(List.of());
        CutoverClosureQueryService service = new CutoverClosureQueryService(taskMapper, approvalMapper, planMapper,
                closureMapper, attachmentMapper, evidenceMapper,
                new CutoverClosureControlledPorts.ProjectScopes(10L, 5L));
        var storage = mock(CutoverClosureStorage.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "storage", storage);

        CutoverClosureView view = service.detail(new cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext(1L, 9L, "query-test", true, true, true), 100L);

        org.mockito.Mockito.verify(storage).requireSame(new CutoverClosureStorage.Source(closure, task));
        assertThat(view.closureVersion()).isEqualTo(1);
        assertThat(view.content().attachments()).extracting(value -> value.purposeCode().name())
                .containsExactly("IMPLEMENTATION_COMMITMENT", "POST_COLLECTION_CHECKLIST");
        assertThat(view.allowedActions()).containsExactly("SAVE_CLOSURE", "REQUEST_COLLECTION", "SUBMIT_CLOSURE");
        for (int permissions = 0; permissions < 8; permissions++) {
            var context = new cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext(1L, 9L, "closure-equivalence",
                    (permissions & 1) != 0, (permissions & 2) != 0, (permissions & 4) != 0);
            assertThat(service.hasWorkbenchAction(new CutoverClosureQueryService.ActionQuery(context, 100L)))
                    .isEqualTo(!service.detail(context, 100L).allowedActions().isEmpty());
        }

        FileArtifactApi files = mock(FileArtifactApi.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "fileArtifacts", files);
        var checklistKey = new FileReferenceSetKey("CUT", "CUTOVER_CLOSURE", "400", "POST_COLLECTION_CHECKLIST");
        when(files.inspectReferenceSets(any())).thenReturn(List.of(new FileReferenceSetFact(checklistKey, 17L,
                List.of(new FileArtifactVersionFact(9007199254740993L, 4, "unsaved-upload-uuid", "CUTOVER_CLOSURE",
                        "photo.png", 10L, "image/png", "a".repeat(64), "AVAILABLE", "ACTIVE",
                        new FileFactVersion(2, 3, 5), 17L)))));
        var context = new cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext(
                1L, 9L, "recover-files", true, true, true);
        var query = new CutoverClosureQueryService.ActionQuery(context, 100L);
        var available = service.availableAttachments(query);
        assertThat(available).hasSize(1);
        assertThat(available.getFirst().artifactId()).isEqualTo(9007199254740993L);
        assertThat(available.getFirst().referenceKey()).isEqualTo("unsaved-upload-uuid");
        assertThat(available.getFirst().fileFactVersion().referenceVersion()).isEqualTo(3);
        verify(files).inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(checklistKey,
                new FileReferenceSetKey("CUT", "CUTOVER_CLOSURE", "400", "IMPLEMENTATION_COMMITMENT"),
                new FileReferenceSetKey("CUT", "CUTOVER_CLOSURE", "400", "OTHER_EVIDENCE")), "READ"));

        clearInvocations(files);
        var withoutSave = new cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext(
                1L, 9L, "read-only", false, true, true);
        assertThatThrownBy(() -> service.availableAttachments(new CutoverClosureQueryService.ActionQuery(withoutSave, 100L)))
                .isInstanceOf(CutoverClosureApplicationException.class);
        task.setOwnerUserId(8L);
        assertThatThrownBy(() -> service.availableAttachments(query)).isInstanceOf(CutoverClosureApplicationException.class);
        task.setOwnerUserId(9L);
        task.setTenantId(2L);
        assertThatThrownBy(() -> service.availableAttachments(query)).isInstanceOf(CutoverClosureApplicationException.class);
        task.setTenantId(1L);
        task.setTaskStatus("ARCHIVED"); task.setVersion(8L); closure.setStatusCode("SUBMITTED");
        assertThatThrownBy(() -> service.availableAttachments(query)).isInstanceOf(CutoverClosureApplicationException.class);
        verifyNoInteractions(files);
    }

    private static CutoverClosureAttachmentDO attachment(long id, String purpose, String reference) {
        CutoverClosureAttachmentDO row = new CutoverClosureAttachmentDO(); row.setId(id); row.setPurposeCode(purpose);
        row.setArtifactId(500L + id); row.setFileVersionNo(1); row.setReferenceKey(reference);
        row.setFileArtifactFactVersion(1L); row.setFileReferenceFactVersion(2L); row.setFileAvailabilityFactVersion(3L);
        row.setFileScopeVersion(4L); row.setFileHash("a".repeat(64)); return row;
    }
}
