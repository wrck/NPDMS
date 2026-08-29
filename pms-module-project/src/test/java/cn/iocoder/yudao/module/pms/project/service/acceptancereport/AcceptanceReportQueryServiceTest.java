package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceReportQueryServiceTest {

    @Mock AcceptanceActivityMapper activityMapper;
    @Mock AcceptanceReportVersionMapper reportMapper;
    @Mock AcceptanceReportAttachmentMapper attachmentMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock FileArtifactApi fileArtifactApi;

    @Test
    void emptyProjectScopeReturnsEmptyActivityList() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of());

        assertTrue(service().list(null, actor()).isEmpty());
    }

    @Test
    void returnsVersionHistoryAndRevalidatesDownloadFact() {
        AcceptanceActivityDO activity = activity();
        AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();
        report.setId(300L);
        report.setAcceptanceId(100L);
        report.setReportVersionNo(1);
        report.setReportStatus("EFFECTIVE");
        AcceptanceReportAttachmentDO attachment = attachment();
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(
                80L, 1L, Set.of(80L), Set.of()));
        when(reportMapper.selectByAcceptanceId(100L)).thenReturn(List.of(report));
        when(reportMapper.selectById(300L)).thenReturn(report);
        when(attachmentMapper.selectByReportVersion(300L)).thenReturn(List.of(attachment));
        when(fileArtifactApi.inspect(any())).thenReturn(file());

        var service = service();
        assertEquals(1, service.listVersions(100L, actor()).size());
        assertEquals(11L, service.getDownloadFact(100L, 300L, 1, actor()).artifactId());
    }

    private AcceptanceReportQueryService service() {
        return new AcceptanceReportQueryService(activityMapper, reportMapper, attachmentMapper,
                projectScopeApi, fileArtifactApi);
    }

    private AcceptanceReportQueryService.Actor actor() {
        return new AcceptanceReportQueryService.Actor(7L, 19L);
    }

    private AcceptanceActivityDO activity() {
        AcceptanceActivityDO row = new AcceptanceActivityDO();
        row.setId(100L);
        row.setProjectId(80L);
        row.setTenantId(7L);
        return row;
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
