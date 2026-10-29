package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class InspectionUmcBundleTest {
    @Test void closureGateLoadsTheBundleSnapshotAfterReadingOnlyReportMetadata() {
        var reports=mock(InspectionReportRevisionMapper.class);var bundle=mock(InspectionUmcBundle.class);
        var metadata=part(70L);metadata.setTemplateVersion(InspectionUmcBundle.TEMPLATE);
        var content=part(70L);content.setTemplateVersion(InspectionUmcBundle.TEMPLATE);
        content.setResultJson(JsonUtils.toJsonString(new InspectionUmcBundle.Snapshot(List.of(60L),List.of(100L))));
        content.setResultSha256(DigestUtil.sha256Hex(content.getResultJson()));
        when(reports.selectById(70L)).thenReturn(metadata);
        when(reports.selectContent(argThat(q->q.tenantId().equals(7L)&&q.taskId().equals(10L)&&q.reportId().equals(70L)))).thenReturn(content);
        when(bundle.available(any())).thenAnswer(inv->{InspectionUmcBundle.snapshot(inv.<InspectionUmcBundle.Query>getArgument(0).report());return true;});
        var service=new cn.iocoder.yudao.module.pms.service.service.inspection.InspectionReportService(bundle,null,null,reports,null,null,null,null,null,null,null,null,null,null,null,null);
        var task=new InspectionTaskDO();task.setTenantId(7L);task.setId(10L);
        assertTrue(service.available(new cn.iocoder.yudao.module.pms.service.service.inspection.InspectionReportService.ReportGate(null,task,70L)));
    }
    @Test void allDeviceOriginalsMustRemainAvailableInTheSameTaskAndAttempt() {
        var reports=mock(InspectionReportRevisionMapper.class);var files=mock(InspectionReportFileMapper.class);var evidence=mock(FileEvidenceApi.class);
        var bundle=new InspectionUmcBundle(reports,files,evidence);var report=new InspectionReportRevisionDO();report.setId(70L);report.setTenantId(7L);report.setTaskId(10L);report.setAttemptId(30L);
        report.setSourceCode("UMC");report.setTemplateVersion(InspectionUmcBundle.TEMPLATE);report.setStatusCode("AVAILABLE");
        report.setResultJson(JsonUtils.toJsonString(new InspectionUmcBundle.Snapshot(List.of(60L,61L),List.of(100L,101L))));report.setResultSha256(DigestUtil.sha256Hex(report.getResultJson()));
        var a=part(60L);var b=part(61L);when(reports.selectById(60L)).thenReturn(a);when(reports.selectById(61L)).thenReturn(b);
        var fa=file(60L,600L);var fb=file(61L,610L);when(files.selectForTask(any())).thenReturn(List.of(fa,fb));
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true,"",1,1,1));
        var query=new InspectionUmcBundle.Query(report);assertTrue(bundle.available(query));assertEquals(2,bundle.files(query).size());
        verify(evidence).lockAndRevalidate(argThat(q->q.artifactId().equals(600L)&&q.purposeCode().equals("UMC_REPORT")));
        when(files.selectForTask(any())).thenReturn(List.of(fa));assertThrows(InspectionException.class,()->bundle.available(query));
        when(files.selectForTask(any())).thenReturn(List.of(fa,fb));b.setAttemptId(31L);assertThrows(InspectionException.class,()->bundle.available(query));b.setAttemptId(30L);
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"unavailable",1,1,1));assertFalse(bundle.available(query));
    }
    private InspectionReportRevisionDO part(Long id) {var r=new InspectionReportRevisionDO();r.setId(id);r.setTenantId(7L);r.setTaskId(10L);r.setAttemptId(30L);r.setSourceCode("UMC");r.setTemplateVersion("umc-doc-v1");r.setStatusCode("AVAILABLE");return r;}
    private InspectionReportFileDO file(Long report,Long artifact) {var f=new InspectionReportFileDO();f.setTenantId(7L);f.setTaskId(10L);f.setReportId(report);f.setArtifactId(artifact);f.setFormatCode("DOC");f.setFileVersionNo(1);f.setReferenceKey("source-"+artifact);f.setSha256("a".repeat(64));return f;}
}
