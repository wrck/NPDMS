package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectDeliverableOwnerSourcesTest {
    final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    final AcceptanceReportVersionMapper reports = mock(AcceptanceReportVersionMapper.class);
    final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    final SatisfactionResultMapper results = mock(SatisfactionResultMapper.class);
    final ProjectDeliverableSourceAttachmentMapper attachments = mock(ProjectDeliverableSourceAttachmentMapper.class);
    final FileEvidenceApi files = mock(FileEvidenceApi.class);
    final ProjectDeliverableOwnerSources service = new ProjectDeliverableOwnerSources(activities, reports, tasks, results, attachments, files);
    final AccProjectDeliverableDO root = new AccProjectDeliverableDO();
    final ProjectDeliverableSourceVersionDO source = new ProjectDeliverableSourceVersionDO();
    final AcceptanceActivityDO activity = new AcceptanceActivityDO();
    final AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();

    @BeforeEach void setup() {
        root.setId(31L); root.setTenantId(7L); root.setProjectId(9L);
        source.setId(88L); source.setTenantId(7L); source.setDeliverableId(31L); source.setRelationStatus("CURRENT");
        source.setSourceObjectType("AcceptanceReportVersion"); source.setSourceObjectId(55L); source.setSourceVersion(1);
        activity.setId(51L); activity.setTenantId(7L); activity.setProjectId(9L); activity.setDeliverableId(31L); activity.setCurrentReportVersionId(55L);
        report.setId(55L); report.setTenantId(7L); report.setAcceptanceId(51L); report.setReportVersionNo(1); report.setReportStatus("EFFECTIVE");
        when(reports.selectById(55L)).thenReturn(report); when(reports.selectByIdForUpdate(any())).thenReturn(report);
        when(activities.selectByIdForUpdate(any())).thenReturn(activity);
        var file = new ProjectDeliverableSourceAttachmentDO(); file.setFileArtifactId(40L); file.setFileVersionNo(1); file.setReferenceKey("report-slot"); file.setFileHash("a".repeat(64));
        when(attachments.selectBySourceVersion(88L)).thenReturn(List.of(file));
        when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, "VALID", 1, 1, 1));
    }
    @Test void currentReportUsesItsExactOriginalFileReference() {
        assertTrue(service.revalidate(root, source).valid());
        verify(files).lockAndRevalidate(new FileEvidenceApi.Query(7L, 40L, 1, "ACC", "ACCEPTANCE_REPORT_VERSION", "55",
                "ACCEPTANCE_REPORT_ATTACHMENT", "report-slot", "a".repeat(64)));
        verify(reports, never()).updateById(any(AcceptanceReportVersionDO.class));
    }
    @Test void revokedOrReplacedReportCannotRemainAccepted() {
        report.setReportStatus("REVOKED"); assertFalse(service.revalidate(root, source).valid());
        report.setReportStatus("EFFECTIVE"); activity.setCurrentReportVersionId(56L);
        assertFalse(service.revalidate(root, source).valid()); verifyNoInteractions(files);
    }
    @Test void anotherProjectOrDeliverableCannotReuseTheSource() {
        activity.setProjectId(10L); assertFalse(service.revalidate(root, source).valid());
        activity.setProjectId(9L); activity.setDeliverableId(32L); assertFalse(service.revalidate(root, source).valid());
        verifyNoInteractions(files);
    }
    @Test void invalidFileFailsDespiteEffectiveReport() {
        when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false, "FILE_EVIDENCE_UNAVAILABLE", 2, 2, 2));
        assertEquals("FILE_EVIDENCE_UNAVAILABLE", service.revalidate(root, source).reason());
    }
    @Test void satisfactionMustStillBeThePassingCurrentResult() {
        source.setSourceObjectType("SatisfactionResult");
        var result = new SatisfactionResultDO(); result.setId(55L); result.setTenantId(7L); result.setCollectionTaskId(60L);
        result.setResultVersion(1); result.setResultStatus("EFFECTIVE"); result.setPassed(true);
        var task = new SatisfactionCollectionTaskDO(); task.setId(60L); task.setTenantId(7L); task.setProjectId(9L); task.setDeliverableId(31L); task.setResultId(55L);
        when(results.selectById(55L)).thenReturn(result); when(results.selectByIdForUpdate(7L, 55L)).thenReturn(result);
        when(tasks.selectByIdForUpdate(7L, 60L)).thenReturn(task);
        assertTrue(service.revalidate(root, source).valid());
        result.setPassed(false); assertFalse(service.revalidate(root, source).valid());
        result.setPassed(true); result.setResultStatus("INVALIDATED"); assertFalse(service.revalidate(root, source).valid());
    }

    @Test void satisfactionSignatureFileValidatesUnderItsOriginalResponseReference() {
        source.setSourceObjectType("SatisfactionResult");
        var result = new SatisfactionResultDO(); result.setId(55L); result.setTenantId(7L); result.setCollectionTaskId(60L);
        result.setResultVersion(1); result.setResultStatus("EFFECTIVE"); result.setPassed(true); result.setResponseId(90L);
        var task = new SatisfactionCollectionTaskDO(); task.setId(60L); task.setTenantId(7L); task.setProjectId(9L); task.setDeliverableId(31L); task.setResultId(55L);
        when(results.selectById(55L)).thenReturn(result); when(results.selectByIdForUpdate(7L, 55L)).thenReturn(result);
        when(tasks.selectByIdForUpdate(7L, 60L)).thenReturn(task);
        var signature = new ProjectDeliverableSourceAttachmentDO(); signature.setFileArtifactId(41L); signature.setFileVersionNo(1);
        signature.setReferenceKey("sig-slot"); signature.setFileHash("b".repeat(64));
        when(attachments.selectBySourceVersion(88L)).thenReturn(List.of(signature));
        // The signature never lives under the result document purpose; its active reference is the response signature set.
        when(files.lockAndRevalidate(new FileEvidenceApi.Query(7L, 41L, 1, "ACC", "SATISFACTION_RESULT", "55",
                "SATISFACTION_RESULT_DOCUMENT", "sig-slot", "b".repeat(64))))
                .thenReturn(new FileEvidenceApi.Fact(false, "FILE_EVIDENCE_UNAVAILABLE", 1, 0, null));
        when(files.lockAndRevalidate(new FileEvidenceApi.Query(7L, 41L, 1, "ACC", "SATISFACTION_RESPONSE", "90",
                "SATISFACTION_SIGNATURE", "sig-slot", "b".repeat(64))))
                .thenReturn(new FileEvidenceApi.Fact(true, "FILE_EVIDENCE_VALID", 1, 0, 0));
        assertTrue(service.revalidate(root, source).valid());
    }

    @Test void satisfactionResultDocumentStillValidatesUnderTheResultReference() {
        source.setSourceObjectType("SatisfactionResult");
        var result = new SatisfactionResultDO(); result.setId(55L); result.setTenantId(7L); result.setCollectionTaskId(60L);
        result.setResultVersion(1); result.setResultStatus("EFFECTIVE"); result.setPassed(true); result.setResponseId(90L);
        var task = new SatisfactionCollectionTaskDO(); task.setId(60L); task.setTenantId(7L); task.setProjectId(9L); task.setDeliverableId(31L); task.setResultId(55L);
        when(results.selectById(55L)).thenReturn(result); when(results.selectByIdForUpdate(7L, 55L)).thenReturn(result);
        when(tasks.selectByIdForUpdate(7L, 60L)).thenReturn(task);
        assertTrue(service.revalidate(root, source).valid());
        verify(files).lockAndRevalidate(new FileEvidenceApi.Query(7L, 40L, 1, "ACC", "SATISFACTION_RESULT", "55",
                "SATISFACTION_RESULT_DOCUMENT", "report-slot", "a".repeat(64)));
    }
}
