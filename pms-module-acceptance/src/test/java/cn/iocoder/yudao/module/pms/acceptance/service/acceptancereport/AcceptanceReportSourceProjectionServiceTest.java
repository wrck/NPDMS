package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AcceptanceReportSourceProjectionServiceTest {

    @Mock AccProjectDeliverableMapper deliverableMapper;
    @Mock ProjectDeliverableSourceVersionMapper sourceMapper;
    @Mock ProjectDeliverableSourceAttachmentMapper sourceAttachmentMapper;

    @Test
    void effectiveReportCreatesOneCurrentSourceAndCompleteAttachmentSet() {
        AccProjectDeliverableDO deliverable = new AccProjectDeliverableDO();
        deliverable.setId(50L);
        deliverable.setProjectId(80L);
        deliverable.setTenantId(7L);
        deliverable.setDeliverableCode("RENAMED_CUSTOM_REPORT");
        deliverable.setVersion(0);
        when(deliverableMapper.selectByIdForUpdate(any())).thenReturn(deliverable);
        when(sourceMapper.selectIdentityForUpdate(any())).thenReturn(null);
        when(sourceMapper.selectCurrentForUpdate(any())).thenReturn(null);
        when(sourceMapper.insert(any(ProjectDeliverableSourceVersionDO.class))).thenReturn(1);
        when(sourceAttachmentMapper.insert(any(ProjectDeliverableSourceAttachmentDO.class))).thenReturn(1);
        when(deliverableMapper.updateById(any(AccProjectDeliverableDO.class))).thenReturn(1);
        var service = new AcceptanceReportSourceProjectionService(
                deliverableMapper, sourceMapper, sourceAttachmentMapper);

        service.project(event());

        ArgumentCaptor<ProjectDeliverableSourceVersionDO> source =
                ArgumentCaptor.forClass(ProjectDeliverableSourceVersionDO.class);
        verify(sourceMapper).insert(source.capture());
        assertEquals("CURRENT", source.getValue().getRelationStatus());
        assertEquals("PENDING_COMPENSATION", source.getValue().getArchiveStatus());
        assertEquals(source.getValue().getId(), deliverable.getCurrentSourceVersionId());
        verify(sourceAttachmentMapper).insert(any(ProjectDeliverableSourceAttachmentDO.class));
        verify(deliverableMapper).selectByIdForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery(7L,50L));
        verify(deliverableMapper,never()).selectByProjectAndCodeForUpdate(any());
    }

    @Test void wrongProjectOrTenantCannotReceiveAReportWithTheSameInstanceId() {
        var row=new AccProjectDeliverableDO(); row.setId(50L); row.setTenantId(7L); row.setProjectId(999L);
        when(deliverableMapper.selectByIdForUpdate(any())).thenReturn(row);
        var service=new AcceptanceReportSourceProjectionService(deliverableMapper,sourceMapper,sourceAttachmentMapper);
        assertThrows(IllegalStateException.class,()->service.project(event()));
        row.setProjectId(80L); row.setTenantId(8L);
        assertThrows(IllegalStateException.class,()->service.project(event()));
        verifyNoInteractions(sourceMapper,sourceAttachmentMapper);
    }

    @Test void missingFrozenIdentityDoesNotFallBackToReportTypeOrCurrentCodes() {
        var input=event();
        var missing=new AcceptanceReportVersionChangedMessage(input.eventId(),input.tenantId(),input.changeType(),
                input.acceptanceId(),input.projectId(),null,input.reportType(),input.publisherActorUserId(),
                input.currentReportVersionId(),input.previousReportVersionId(),input.reportVersionNo(),input.attachments());
        var service=new AcceptanceReportSourceProjectionService(deliverableMapper,sourceMapper,sourceAttachmentMapper);
        assertThrows(IllegalArgumentException.class,()->service.project(missing));
        verifyNoInteractions(deliverableMapper,sourceMapper,sourceAttachmentMapper);
    }

    @Test void revocationAfterRenameUsesTheOriginalInstanceAndKeepsItsBinding() {
        var row=new AccProjectDeliverableDO(); row.setId(50L); row.setTenantId(7L); row.setProjectId(80L);
        row.setDeliverableCode("RENAMED"); row.setVersion(3); row.setCurrentSourceVersionId(61L);
        var source=new ProjectDeliverableSourceVersionDO(); source.setId(61L); source.setDeliverableId(50L); source.setSourceObjectId(300L);
        when(deliverableMapper.selectByIdForUpdate(any())).thenReturn(row);
        when(sourceMapper.selectCurrentForUpdate(any())).thenReturn(source);
        when(sourceMapper.updateById(source)).thenReturn(1); when(deliverableMapper.updateById(row)).thenReturn(1);
        var service=new AcceptanceReportSourceProjectionService(deliverableMapper,sourceMapper,sourceAttachmentMapper);
        service.project(new AcceptanceReportVersionChangedMessage("revoke",7L,"REVOKED",100L,80L,50L,"PRELIMINARY",19L,null,300L,1,List.of()));
        assertEquals("REVOKED",source.getRelationStatus()); assertEquals(50L,source.getDeliverableId());
        assertEquals("RENAMED",row.getDeliverableCode());
        verify(deliverableMapper,never()).selectByProjectAndCodeForUpdate(any());
    }

    private AcceptanceReportVersionChangedMessage event() {
        FileArtifactVersionFact fact = new FileArtifactVersionFact(11L, 2, "slot-a", null,
                null, null, null, "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 8L);
        return new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE", 100L, 80L, 50L,
                "PRELIMINARY", 19L, 300L, null, 1, List.of(fact));
    }
}
