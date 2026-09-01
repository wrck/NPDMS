package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        task.setOwnerUserId(9L); task.setVersion(7);
        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO(); plan.setId(300L); plan.setCutoverTaskId(100L);
        plan.setRevisionNo(2); plan.setStatusCode("SUBMITTED"); plan.setApprovalInstanceId(200L);
        plan.setApprovalVersion(4); plan.setVersion(6);
        CutoverApprovalInstanceDO approval = new CutoverApprovalInstanceDO(); approval.setId(200L);
        approval.setTaskId(100L); approval.setPlanRevisionId(300L); approval.setStatusCode("APPROVED");
        approval.setVersion(4);
        CutoverClosureDO closure = new CutoverClosureDO(); closure.setId(400L); closure.setTenantId(1L);
        closure.setTaskId(100L); closure.setPlanRevisionId(300L); closure.setApprovalInstanceId(200L);
        closure.setStatusCode("DRAFT"); closure.setVersion(1); closure.setPreCheckNormal(true);
        closure.setExecutionNormal(true); closure.setTestNormal(true); closure.setRollbackOccurred(false);
        closure.setFinalResultCode("SUCCESS");
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

        CutoverClosureView view = service.detail(1L, 9L, 100L,
                new CutoverClosureQueryService.ClosureAccess(true, true, true));

        assertThat(view.closureVersion()).isEqualTo(1);
        assertThat(view.content().attachments()).extracting(value -> value.purposeCode().name())
                .containsExactly("IMPLEMENTATION_COMMITMENT", "POST_COLLECTION_CHECKLIST");
        assertThat(view.allowedActions()).containsExactly("SAVE_CLOSURE", "REQUEST_COLLECTION", "SUBMIT_CLOSURE");
    }

    private static CutoverClosureAttachmentDO attachment(long id, String purpose, String reference) {
        CutoverClosureAttachmentDO row = new CutoverClosureAttachmentDO(); row.setId(id); row.setPurposeCode(purpose);
        row.setArtifactId(500L + id); row.setFileVersionNo(1); row.setReferenceKey(reference);
        row.setFileFactVersion("{\"artifactVersion\":1,\"referenceVersion\":2,\"availabilityVersion\":3}");
        row.setFileScopeVersion(4L); row.setFileHash("a".repeat(64)); return row;
    }
}
