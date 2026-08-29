package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceReportCommandServiceTest {

    @Mock AcceptanceActivityMapper activityMapper;
    @Mock AcceptanceReportVersionMapper reportMapper;
    @Mock AcceptanceReportAttachmentMapper attachmentMapper;
    @Mock FileArtifactApi fileArtifactApi;
    @Mock PlatformCommandExecutionApi commandExecutionApi;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void publishesCompleteDraftAndFreezesOrderedPublicFileFacts() {
        AcceptanceActivityDO activity = activity("PRELIMINARY", 0, null);
        AcceptanceReportVersionDO draft = report(300L, 1, "DRAFT");
        when(activityMapper.selectByIdForUpdate(any())).thenReturn(activity);
        when(reportMapper.selectCurrentForUpdate(any())).thenReturn(null);
        when(reportMapper.selectByIdForUpdate(any())).thenReturn(draft);
        FileArtifactVersionFact file = file();
        FileReferenceSetFact set = new FileReferenceSetFact(new FileReferenceSetKey(
                "ACC", "ACCEPTANCE_REPORT_VERSION", "300", "ACCEPTANCE_REPORT_ATTACHMENT"),
                8L, List.of(file));
        when(fileArtifactApi.inspectReferenceSets(any())).thenReturn(List.of(set));
        when(fileArtifactApi.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));
        when(reportMapper.updateById(any(AcceptanceReportVersionDO.class))).thenReturn(1);
        when(attachmentMapper.insert(any(AcceptanceReportAttachmentDO.class))).thenReturn(1);
        when(activityMapper.updateById(any(AcceptanceActivityDO.class))).thenReturn(1);
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(reportMapper.selectById(300L)).thenReturn(draft);
        when(attachmentMapper.selectByReportVersion(300L)).thenAnswer(ignored -> List.of(attachment(file)));
        executeCommandsImmediately();
        var service = service();

        var result = service.publish(new AcceptanceReportCommands.PublishCommand(
                100L, 300L, 0, 1, null, "key-1", "digest-1"), actor());

        assertEquals("EFFECTIVE", result.reportStatus());
        assertEquals("EFFECTIVE", result.changeType());
        assertEquals(19L, draft.getPublisherUserId());
        assertEquals(300L, activity.getCurrentReportVersionId());
        assertEquals(1, activity.getVersion());
        verify(attachmentMapper).insert(any(AcceptanceReportAttachmentDO.class));
    }

    @Test
    void revokeClearsCurrentWithoutRestoringPreviousVersion() {
        AcceptanceActivityDO activity = activity("PRELIMINARY", 2, 300L);
        AcceptanceReportVersionDO current = report(300L, 2, "EFFECTIVE");
        current.setPreviousVersionId(200L);
        current.setPublisherUserId(19L);
        when(activityMapper.selectByIdForUpdate(any())).thenReturn(activity);
        when(reportMapper.selectCurrentForUpdate(any())).thenReturn(current);
        when(reportMapper.updateById(any(AcceptanceReportVersionDO.class))).thenReturn(1);
        when(activityMapper.updateById(any(AcceptanceActivityDO.class))).thenReturn(1);
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(reportMapper.selectById(300L)).thenReturn(current);
        executeCommandsImmediately();
        var service = service();

        var result = service.revoke(new AcceptanceReportCommands.RevokeCommand(
                100L, 2, 300L, 2, "key-2", "digest-2"), actor());

        assertEquals("REVOKED", result.reportStatus());
        assertNull(activity.getCurrentReportVersionId());
        assertEquals("REVOKED", current.getReportStatus());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeCommandsImmediately() {
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier operation = invocation.getArgument(3);
            Object result = operation.get();
            Function facts = invocation.getArgument(4);
            facts.apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }

    private AcceptanceReportCommandService service() {
        return new AcceptanceReportCommandService(activityMapper, reportMapper, attachmentMapper,
                fileArtifactApi, commandExecutionApi);
    }

    private AcceptanceReportCommands.Actor actor() {
        return new AcceptanceReportCommands.Actor(7L, 19L, "corr-1");
    }

    private AcceptanceActivityDO activity(String type, int version, Long currentReportId) {
        AcceptanceActivityDO row = new AcceptanceActivityDO();
        row.setId(100L);
        row.setProjectId(80L);
        row.setProjectTaskId(90L);
        row.setAcceptanceType(type);
        row.setActivityStatus("PENDING");
        row.setCurrentReportVersionId(currentReportId);
        row.setVersion(version);
        row.setTenantId(7L);
        return row;
    }

    private AcceptanceReportVersionDO report(Long id, int versionNo, String status) {
        AcceptanceReportVersionDO row = new AcceptanceReportVersionDO();
        row.setId(id);
        row.setAcceptanceId(100L);
        row.setReportVersionNo(versionNo);
        row.setReportStatus(status);
        row.setAcceptanceTime(LocalDateTime.of(2026, 8, 30, 10, 0));
        row.setConclusionCode("PASS");
        row.setAcceptorName("验收人");
        row.setTenantId(7L);
        return row;
    }

    private FileArtifactVersionFact file() {
        return new FileArtifactVersionFact(11L, 2, "7a5d9177-2f67-4bb5-a211-b0b612e72e5f",
                "ACCEPTANCE_REPORT_ATTACHMENT", "report.pdf", 10L, "application/pdf", "a".repeat(64),
                "AVAILABLE", "ACTIVE", new FileFactVersion(3, 4, 5), 8L);
    }

    private AcceptanceReportAttachmentDO attachment(FileArtifactVersionFact fact) {
        AcceptanceReportAttachmentDO row = new AcceptanceReportAttachmentDO();
        row.setFileArtifactId(fact.artifactId());
        row.setFileVersionNo(fact.versionNo());
        row.setReferenceKey(fact.referenceKey());
        row.setArtifactVersion(3);
        row.setReferenceVersion(4);
        row.setAvailabilityVersion(5);
        row.setScopeVersion(8L);
        row.setFileHash(fact.sha256());
        return row;
    }
}
