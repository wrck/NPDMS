package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        deliverable.setDeliverableCode("D-INITIAL-REPORT");
        deliverable.setVersion(0);
        when(deliverableMapper.selectByProjectAndCodeForUpdate(any())).thenReturn(deliverable);
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
    }

    private AcceptanceReportVersionChangedMessage event() {
        FileArtifactVersionFact fact = new FileArtifactVersionFact(11L, 2, "slot-a", null,
                null, null, null, "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 8L);
        return new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE", 100L, 80L,
                "PRELIMINARY", 19L, 300L, null, 1, List.of(fact));
    }
}
