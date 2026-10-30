package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverApprovalResultReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverApprovalResultReferenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCalculator;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalStorageTest {
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void readsFrozenResultIdEvenAfterSelectionAndPlanVersionAdvance() {
        var f = new Fixture("A");
        var item = new CutoverChecklistItemDO(); item.setId(710L); item.setTenantId(1L); item.setChecklistId(700L);
        item.setStableItemKey("RISK_CHECK"); item.setItemTypeCode("RISK"); item.setItemName("Frozen risk");
        item.setItemDefinitionId(720L); item.setItemDefinitionVersion(1);
        var result = new CutoverChecklistItemResultDO(); result.setId(730L); result.setTenantId(1L);
        result.setChecklistItemId(710L); result.setResultVersion(2); result.setResultSourceCode("DIRECT");
        result.setCurrentMarker(null); // a later result may now be CURRENT
        var reference = CutoverApprovalResultReferenceDO.builder().tenantId(1L).approvalInstanceId(500L)
                .checklistItemId(710L).checklistResultId(730L).resultVersion(2).requiredFlag(true).build();
        when(f.references.selectListByParents(any())).thenReturn(List.of(reference));
        when(f.items.selectById(710L)).thenReturn(item); when(f.results.selectById(730L)).thenReturn(result);
        when(f.resultStorage.answer(any())).thenReturn("{\"value\":{\"normal\":true}}");
        f.plan.setVersion(8L);

        var source = f.store.source(f.root);

        assertEquals(2L, source.plan().planVersion());
        assertEquals("Project One", source.project().projectName());
        assertEquals(2, source.riskItems().getFirst().itemResultVersion());
        assertEquals("{\"value\":{\"normal\":true}}", source.riskItems().getFirst().answerSnapshot());
        verify(f.results).selectById(730L);
        verifyNoMoreInteractions(f.results);
        result.setResultVersion(3);
        assertThrows(IllegalArgumentException.class, () -> f.store.source(f.root));
    }

    @Test void rejectsWrongTenantAndSourceIdentityWithoutLegacyJsonFallback() {
        var f = new Fixture("A");
        f.root.setSourceSnapshot(CutoverApprovalControlledPorts.approvalSource("A"));
        f.root.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> f.store.source(f.root));
        verifyNoInteractions(f.assessments);
        f.root.setTenantId(1L); f.root.setAssessmentVersion(99L);
        assertThrows(IllegalArgumentException.class, () -> CutoverApprovalStorage.applySource(
                f.root, CutoverApprovalControlledPorts.approvalSource("A")));
        assertThrows(IllegalArgumentException.class, () -> f.store.source(f.root));
    }

    @Test void restoresGradeDNullChecklistAndRejectsForeignApprovalReference() {
        var f = new Fixture("D");
        var source = f.store.source(f.root);
        assertNull(source.checklistId());
        assertTrue(source.plan().sourceSnapshot().has("checklistId"));
        assertTrue(source.plan().sourceSnapshot().get("checklistId").isNull());
        when(f.references.selectListByParents(any())).thenReturn(List.of(CutoverApprovalResultReferenceDO.builder()
                .tenantId(1L).approvalInstanceId(501L).build()));
        assertThrows(IllegalArgumentException.class, () -> f.store.source(f.root));
        verifyNoInteractions(f.results);
    }

    @Test void preservesLeadTimeFactsAndRejectsAContradictoryDisabledRecord() {
        var f = new Fixture("A");
        var fact = new CutoverLeadTimeCalculator().calculate("A", "VERSION_UPGRADE",
                f.root.getSourceScheduleTime(), f.root.getSourceScheduleTime().minusDays(2));
        CutoverApprovalStorage.applyLeadTime(f.root, fact);
        assertEquals(fact, CutoverApprovalStorage.leadTime(f.root));
        f.root.setLeadActualNaturalDays(3);
        assertThrows(IllegalArgumentException.class, () -> CutoverApprovalStorage.leadTime(f.root));
        CutoverApprovalStorage.applyLeadTime(f.root, null);
        assertNull(CutoverApprovalStorage.leadTime(f.root));
        f.root.setLeadRequiredDays(2);
        assertThrows(IllegalArgumentException.class, () -> CutoverApprovalStorage.leadTime(f.root));
    }

    private static final class Fixture {
        final CutoverApprovalResultReferenceMapper references = mock(CutoverApprovalResultReferenceMapper.class);
        final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
        final CutoverChecklistItemResultMapper results = mock(CutoverChecklistItemResultMapper.class);
        final CutoverChecklistResultStore resultStorage = mock(CutoverChecklistResultStore.class);
        final CutoverAssessmentMapper assessments = mock(CutoverAssessmentMapper.class);
        final CutoverPlanRevisionMapper plans = mock(CutoverPlanRevisionMapper.class);
        final CutoverPlanStorage planStorage = mock(CutoverPlanStorage.class);
        final CutoverApprovalStorage store = new CutoverApprovalStorage(references, items, results, resultStorage,
                mock(CutoverCustomResultStore.class), assessments, plans, mock(CutoverPlanStepMapper.class),
                mock(CutoverSupportArrangementMapper.class), planStorage);
        final CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
        final CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();
        Fixture(String grade) {
            root.setId(500L); root.setTenantId(1L); root.setTaskId(100L); root.setProjectId(10L);
            root.setAssessmentId(600L); root.setAssessmentVersion(2L); root.setGradeCode(grade);
            root.setChecklistId("D".equals(grade) ? null : 700L); root.setChecklistVersion("D".equals(grade) ? null : 3L);
            root.setPlanRevisionId(900L); root.setPlanRevisionNo(1); root.setSourceSnapshotVersion(1);
            var source = CutoverApprovalStorage.applySource(root, CutoverApprovalControlledPorts.approvalSource(grade));
            var assessment = new CutoverAssessmentDO(); assessment.setId(600L); assessment.setTenantId(1L);
            assessment.setCutoverTaskId(100L); assessment.setAssessmentVersion(2L); assessment.setQuestionnaireTemplateVersion(7001L);
            assessment.setBusinessImportanceLevel("HIGH"); assessment.setOperationComplexityLevel("MEDIUM");
            assessment.setHiddenRiskLevel("LOW"); assessment.setSparePartAppliedFlag(false);
            assessment.setManualGrade(grade); assessment.setServiceLevelCode("GOLD"); assessment.setSubmittedBy(202L);
            assessment.setSubmittedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(source.assessment().submittedAt()), ZoneId.systemDefault()));
            when(assessments.selectById(600L)).thenReturn(assessment);
            plan.setId(900L); plan.setTenantId(1L); plan.setCutoverTaskId(100L); plan.setRevisionNo(1); plan.setVersion(2L);
            plan.setEditModeCode("FULL_FILE_UPLOAD"); plan.setOwnershipConfirmed(true); plan.setFileArtifactId(3301L);
            plan.setFileVersionNo(1); plan.setFileReferenceKey("cut-plan-file"); plan.setFileScopeVersion(2L);
            plan.setFileSha256("a".repeat(64)); plan.setFileArtifactFactVersion(1L);
            plan.setFileReferenceFactVersion(1L); plan.setFileAvailabilityFactVersion(1L);
            when(plans.selectById(900L)).thenReturn(plan);
            var planSource = source.plan().sourceSnapshot().deepCopy();
            if ("D".equals(grade)) ((tools.jackson.databind.node.ObjectNode) planSource).remove("checklistId");
            when(planStorage.sourceView(plan)).thenReturn(planSource);
            when(references.selectListByParents(any())).thenReturn(List.of());
        }
    }
}
