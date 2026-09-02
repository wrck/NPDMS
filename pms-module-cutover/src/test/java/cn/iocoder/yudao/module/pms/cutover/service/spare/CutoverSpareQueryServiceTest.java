package cn.iocoder.yudao.module.pms.cutover.service.spare;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverSpareQueryServiceTest {

    @Test
    void projectsDualSourceApplicationsAndAuthorizedFileDisplayName() {
        Fixture f = new Fixture();
        CutoverTaskDO task = task(); task.setCurrentAssessmentId(30L);
        when(f.tasks.selectById(10L)).thenReturn(task);
        when(f.assessments.selectById(30L)).thenReturn(assessment());
        when(f.checklists.selectCurrent(any())).thenReturn(checklist());
        when(f.items.selectListByChecklist(any())).thenReturn(List.of(riskItem()));
        CutoverSpareApplicationReferenceDO application = application();
        when(f.applications.selectByTask(any())).thenReturn(List.of(application));
        when(f.statuses.selectByApplication(any())).thenReturn(List.of(status()));
        when(f.evidence.selectByTask(any())).thenReturn(List.of(evidence()));

        var detail = f.service.detail(1L, 10L, 11L,
                new CutoverSpareQueryService.ActionAccess(true, true, true, true));

        assertThat(detail.need().sources()).extracting(source -> source.sourceType())
                .containsExactly("ASSESSMENT", "CHECKLIST_RISK");
        assertThat(detail.allowedActions()).containsExactly("INITIATE", "REFRESH", "ADD_EVIDENCE");
        assertThat(detail.applications()).singleElement().satisfies(view -> {
            assertThat(view.currentStatus().snapshot()).containsEntry("progress", 60);
            assertThat(view.requestId()).isEqualTo("REQ-1");
        });
        assertThat(detail.manualEvidence()).singleElement().satisfies(view -> {
            assertThat(view.fileFact().displayName()).isEqualTo("备件签收单.pdf");
            assertThat(view.fileFact().referenceKey()).isEqualTo("ref-1");
        });
        assertThat(f.filePort.lastExpectation.referenceKey()).isEqualTo("ref-1");
    }

    @Test
    void doesNotOfferInitiateWhenCurrentSourcesDoNotRequireSpareSupport() {
        Fixture f = new Fixture();
        when(f.tasks.selectById(10L)).thenReturn(task());
        when(f.checklists.selectCurrent(any())).thenReturn(null);
        when(f.applications.selectByTask(any())).thenReturn(List.of());
        when(f.evidence.selectByTask(any())).thenReturn(List.of());

        var detail = f.service.detail(1L, 10L, 11L,
                new CutoverSpareQueryService.ActionAccess(true, true, true, true));

        assertThat(detail.need().required()).isFalse();
        assertThat(detail.allowedActions()).containsExactly("ADD_EVIDENCE");
        verifyNoInteractions(f.assessments, f.items, f.filePort);
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(10L); row.setTenantId(1L); row.setProjectId(20L);
        row.setVersion(4); return row;
    }

    private static CutoverAssessmentDO assessment() {
        CutoverAssessmentDO row = new CutoverAssessmentDO(); row.setId(30L); row.setTenantId(1L);
        row.setCutoverTaskId(10L); row.setAssessmentVersion(2); row.setAssessmentStatus("SUBMITTED");
        row.setCurrentMarker(1); row.setAnswerSnapshot("{\"businessImportanceLevel\":\"HIGH\","
                + "\"operationComplexityLevel\":\"MEDIUM\",\"hiddenRiskLevel\":\"LOW\","
                + "\"sparePartApplied\":true}"); return row;
    }

    private static CutoverChecklistDO checklist() {
        CutoverChecklistDO row = new CutoverChecklistDO(); row.setId(40L); row.setTenantId(1L);
        row.setCutoverTaskId(10L); row.setCurrentMarker(1); return row;
    }

    private static CutoverChecklistItemDO riskItem() {
        CutoverChecklistItemDO row = new CutoverChecklistItemDO(); row.setId(41L); row.setTenantId(1L);
        row.setChecklistId(40L); row.setStableItemKey("MAJOR_PROJECT_SPARES"); row.setApplicableFlag(true);
        row.setVersion(3); return row;
    }

    private static CutoverSpareApplicationReferenceDO application() {
        CutoverSpareApplicationReferenceDO row = new CutoverSpareApplicationReferenceDO();
        row.setId(50L); row.setTenantId(1L); row.setCutoverTaskId(10L); row.setProjectId(20L);
        row.setPlatformRequestId("REQ-1"); row.setIntegrationStatus("EXTERNAL_REFERENCED");
        row.setExternalSystemCode("SPARE"); row.setExternalRequestId("EXT-REQ-1");
        row.setExternalApplicationNo("APP-1"); row.setCurrentStatusRevisionId(51L);
        row.setRetryCount(0); row.setVersion(1); row.setUpdateTime(LocalDateTime.of(2026, 9, 2, 8, 0)); return row;
    }

    private static CutoverSpareStatusRevisionDO status() {
        CutoverSpareStatusRevisionDO row = new CutoverSpareStatusRevisionDO(); row.setId(51L); row.setTenantId(1L);
        row.setApplicationReferenceId(50L); row.setStatusVersion(1L); row.setExternalStatusRaw("PROCESSING");
        row.setStatusSnapshot("{\"progress\":60}"); row.setSourceType("CALLBACK"); row.setCurrentMarker(1);
        row.setObservedAt(LocalDateTime.of(2026, 9, 2, 8, 1)); return row;
    }

    private static CutoverSpareManualEvidenceDO evidence() {
        CutoverSpareManualEvidenceDO row = new CutoverSpareManualEvidenceDO(); row.setId(60L); row.setTenantId(1L);
        row.setCutoverTaskId(10L); row.setApplicationReferenceId(50L); row.setFileArtifactId(70L);
        row.setFileReferenceKey("ref-1"); row.setFileVersionNo(1);
        row.setFileFactVersion("{\"artifactVersion\":1,\"referenceVersion\":2,\"availabilityVersion\":3}");
        row.setFileScopeVersion(4L); row.setDescription("现场签收"); row.setUploadedBy(11L);
        row.setCreatedAt(LocalDateTime.of(2026, 9, 2, 8, 2)); return row;
    }

    private static final class ControlledFilePort implements CutoverSpareFilePort {
        private FileExpectation lastExpectation;
        @Override public FileFact inspect(FileExpectation expectation) {
            lastExpectation = expectation;
            return new FileFact(expectation.artifactId(), expectation.referenceKey(), expectation.versionNo(),
                    expectation.fileFactVersion(), expectation.scopeVersion(), "备件签收单.pdf");
        }
        @Override public FileFact lockAndRevalidate(FileExpectation expectation) { return inspect(expectation); }
    }

    private static final class Fixture {
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final CutoverAssessmentMapper assessments = mock(CutoverAssessmentMapper.class);
        final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
        final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
        final CutoverSpareApplicationReferenceMapper applications = mock(CutoverSpareApplicationReferenceMapper.class);
        final CutoverSpareStatusRevisionMapper statuses = mock(CutoverSpareStatusRevisionMapper.class);
        final CutoverSpareManualEvidenceMapper evidence = mock(CutoverSpareManualEvidenceMapper.class);
        final ControlledFilePort filePort = spy(new ControlledFilePort());
        final CutoverSpareQueryService service = new CutoverSpareQueryService(tasks, applications, statuses, evidence,
                new CutoverSpareNeedAssembler(assessments, checklists, items), filePort);
    }
}
