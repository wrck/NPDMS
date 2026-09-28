package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AcceptanceReportQueryServiceTest {

    private final AcceptanceActivityMapper activityMapper = mock(AcceptanceActivityMapper.class);
    private final AcceptanceReportVersionMapper reportMapper = mock(AcceptanceReportVersionMapper.class);
    private final AcceptanceReportAttachmentMapper attachmentMapper = mock(AcceptanceReportAttachmentMapper.class);
    private final ProjectScopeApi projectScopeApi = mock(ProjectScopeApi.class);
    private final FileArtifactApi fileArtifactApi = mock(FileArtifactApi.class);
    private final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);

    private final AcceptanceReportQueryService service = new AcceptanceReportQueryService(
            activityMapper, reportMapper, attachmentMapper, projectScopeApi, fileArtifactApi, platform);

    @Test
    void emptyProjectScopeReturnsEmptyActivityList() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of());

        assertTrue(service.list(null, actor()).isEmpty());
    }

    @Test
    void returnsVersionHistoryAndRevalidatesDownloadFact() {
        AcceptanceActivityDO activity = activity(null);
        AcceptanceReportVersionDO report = report();
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(80L, 1L, Set.of(80L), Set.of()));
        when(reportMapper.selectByAcceptanceId(100L)).thenReturn(List.of(report));
        when(reportMapper.selectById(300L)).thenReturn(report);
        when(attachmentMapper.selectByReportVersion(300L)).thenReturn(List.of(attachment()));
        when(fileArtifactApi.inspect(any())).thenReturn(file());

        var view = service.listVersions(100L, actor()).getFirst();
        assertNull(view.archiveStatus());
        assertNull(view.archiveFailureCode());
        assertNull(view.archiveRetryCount());
        assertEquals(11L, service.getDownloadFact(100L, 300L, 1, actor()).artifactId());
        verifyNoInteractions(platform);
    }

    @Test
    void archiveMirrorAggregatesTheWorstPendingStateAcrossSubmissionMaterials() {
        when(activityMapper.selectById(100L)).thenReturn(activity(31L));
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(80L, 1L, Set.of(80L), Set.of()));
        when(reportMapper.selectByAcceptanceId(100L)).thenReturn(List.of(report()));
        when(attachmentMapper.selectByReportVersion(300L)).thenReturn(List.of());
        when(platform.findSubmissionByRequestKey(31L, "report:300")).thenReturn(Optional.of(submission()));
        when(platform.listMaterials(31L)).thenReturn(List.of(
                material(701L, PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED, null, null),
                material(702L, PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION,
                        "ARCHIVE_TIMEOUT", 1),
                material(703L, PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION,
                        "ARCHIVE_SCOPE_LOST", 3)));

        var view = service.listVersions(100L, actor()).getFirst();

        assertEquals(PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION, view.archiveStatus());
        assertEquals("ARCHIVE_TIMEOUT", view.archiveFailureCode());
        assertEquals(3, view.archiveRetryCount());
    }

    @Test
    void archiveMirrorReportsArchivedOnlyWhenEveryMaterialClosedAndInvalidWinsOverPending() {
        when(activityMapper.selectById(100L)).thenReturn(activity(31L));
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(80L, 1L, Set.of(80L), Set.of()));
        when(reportMapper.selectByAcceptanceId(100L)).thenReturn(List.of(report()));
        when(attachmentMapper.selectByReportVersion(300L)).thenReturn(List.of());
        when(platform.findSubmissionByRequestKey(31L, "report:300")).thenReturn(Optional.of(submission()));
        when(platform.listMaterials(31L)).thenReturn(List.of(
                material(701L, PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED, null, null),
                material(702L, PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED, null, null)));
        assertEquals(PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED,
                service.listVersions(100L, actor()).getFirst().archiveStatus());

        when(platform.listMaterials(31L)).thenReturn(List.of(
                material(701L, PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED, null, null),
                material(702L, PlatformDeliveryRequirementApi.ARCHIVE_INVALID, "ARCHIVE_REJECTED", 2),
                material(703L, PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION, null, null)));
        var view = service.listVersions(100L, actor()).getFirst();
        assertEquals(PlatformDeliveryRequirementApi.ARCHIVE_INVALID, view.archiveStatus());
        assertEquals("ARCHIVE_REJECTED", view.archiveFailureCode());
        assertEquals(2, view.archiveRetryCount());
    }

    @Test
    void missingSubmissionOrEmptyMaterialSetLeavesTheMirrorBlank() {
        when(activityMapper.selectById(100L)).thenReturn(activity(31L));
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(80L, 1L, Set.of(80L), Set.of()));
        when(reportMapper.selectByAcceptanceId(100L)).thenReturn(List.of(report()));
        when(attachmentMapper.selectByReportVersion(300L)).thenReturn(List.of());
        when(platform.findSubmissionByRequestKey(31L, "report:300")).thenReturn(Optional.empty());
        assertNull(service.listVersions(100L, actor()).getFirst().archiveStatus());

        when(platform.findSubmissionByRequestKey(31L, "report:300")).thenReturn(Optional.of(
                new TemplateFrozenSubmissionView(1001L, 31L, "report:300",
                        PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", "{}", null,
                        List.of(), null)));
        assertNull(service.listVersions(100L, actor()).getFirst().archiveStatus());

        when(platform.findSubmissionByRequestKey(31L, "report:300")).thenReturn(Optional.of(submission()));
        when(platform.listMaterials(31L)).thenReturn(List.of());
        assertNull(service.listVersions(100L, actor()).getFirst().archiveStatus());
    }

    private AcceptanceReportQueryService.Actor actor() {
        return new AcceptanceReportQueryService.Actor(7L, 19L);
    }

    private AcceptanceActivityDO activity(Long deliverableId) {
        AcceptanceActivityDO row = new AcceptanceActivityDO();
        row.setId(100L);
        row.setProjectId(80L);
        row.setTenantId(7L);
        row.setDeliverableId(deliverableId);
        return row;
    }

    private AcceptanceReportVersionDO report() {
        AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();
        report.setId(300L);
        report.setAcceptanceId(100L);
        report.setReportVersionNo(1);
        report.setReportStatus("EFFECTIVE");
        return report;
    }

    private TemplateFrozenSubmissionView submission() {
        return new TemplateFrozenSubmissionView(1001L, 31L, "report:300",
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", "{}", null,
                List.of(701L, 702L, 703L), null);
    }

    private TemplateFrozenMaterialView material(long id, String archiveStatus, String failureCode,
                                                Integer retryCount) {
        return new TemplateFrozenMaterialView(id, 31L, "FILE", 40L, 400L, 1, "a".repeat(64),
                "report.pdf", null, null, null, "ACTIVE", archiveStatus, failureCode, retryCount);
    }

    private AcceptanceReportAttachmentDO attachment() {
        AcceptanceReportAttachmentDO row = new AcceptanceReportAttachmentDO();
        row.setAttachmentSequence(1);
        row.setFileArtifactId(11L);
        row.setFileVersionNo(2);
        row.setReferenceKey("reference-1");
        row.setArtifactVersion(3);
        row.setReferenceVersion(4);
        row.setAvailabilityVersion(5);
        row.setScopeVersion(8L);
        row.setFileHash("a".repeat(64));
        return row;
    }

    private FileArtifactVersionFact file() {
        return new FileArtifactVersionFact(11L, 2, "reference-1", "ACCEPTANCE_REPORT_ATTACHMENT",
                "report.pdf", 10L, "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 8L);
    }
}
