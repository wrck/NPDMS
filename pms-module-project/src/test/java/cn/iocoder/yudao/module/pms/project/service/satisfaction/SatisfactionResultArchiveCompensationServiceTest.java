package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultFileMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SatisfactionResultArchiveCompensationServiceTest {
    @Test
    void archivesDocumentSignatureAndAttachmentThenClosesCurrentProjection() {
        AccProjectDeliverableMapper rootMapper = mock(AccProjectDeliverableMapper.class);
        ProjectDeliverableSourceVersionMapper sourceMapper = mock(ProjectDeliverableSourceVersionMapper.class);
        ProjectDeliverableSourceAttachmentMapper attachmentMapper = mock(ProjectDeliverableSourceAttachmentMapper.class);
        SatisfactionResultMapper resultMapper = mock(SatisfactionResultMapper.class);
        SatisfactionResultFileMapper resultFileMapper = mock(SatisfactionResultFileMapper.class);
        FileArtifactApi fileApi = mock(FileArtifactApi.class);
        ProjectDeliverableSourceVersionDO source = source();
        AccProjectDeliverableDO root = root();
        when(sourceMapper.selectById(30L)).thenReturn(source);
        when(sourceMapper.selectByIdForUpdate(any())).thenReturn(source);
        when(rootMapper.selectByIdForUpdate(any())).thenReturn(root);
        when(resultMapper.selectByIdForUpdate(7L, 40L)).thenReturn(result());
        when(attachmentMapper.selectBySourceVersion(30L)).thenReturn(List.of(
                sourceFile(1, 100L, "doc"), sourceFile(2, 101L, "sig"), sourceFile(3, 102L, "att")));
        when(resultFileMapper.selectListByResult(any())).thenReturn(List.of(
                resultFile("RESULT_DOCUMENT", 1, 100L, "doc"),
                resultFile("SIGNATURE", 1, 101L, "sig"),
                resultFile("ATTACHMENT", 1, 102L, "att")));
        when(sourceMapper.updateById(source)).thenReturn(1);
        when(resultMapper.updateArchiveProjection(any())).thenReturn(1);
        when(rootMapper.updateById(root)).thenReturn(1);
        var service = new SatisfactionResultArchiveCompensationService(rootMapper, sourceMapper,
                attachmentMapper, resultMapper, resultFileMapper, fileApi);

        service.archive(7L, 30L);

        ArgumentCaptor<ArchiveFileReferenceSetsCommand> commands =
                ArgumentCaptor.forClass(ArchiveFileReferenceSetsCommand.class);
        verify(fileApi, org.mockito.Mockito.times(3)).archiveReferenceSets(commands.capture());
        assertEquals(List.of("SATISFACTION_RESULT_DOCUMENT", "SATISFACTION_SIGNATURE", "SATISFACTION_ATTACHMENT"),
                commands.getAllValues().stream().map(value -> value.attachmentSetKey().purposeCode()).toList());
        assertEquals("ARCHIVED", source.getArchiveStatus());
        assertEquals("ARCHIVED", root.getArchiveStatus());
    }

    private ProjectDeliverableSourceVersionDO source() {
        ProjectDeliverableSourceVersionDO row = new ProjectDeliverableSourceVersionDO();
        row.setId(30L); row.setTenantId(7L); row.setDeliverableId(20L); row.setSourceObjectType("SatisfactionResult");
        row.setSourceObjectId(40L); row.setRelationStatus("CURRENT"); row.setArchiveStatus("PENDING_COMPENSATION");
        row.setArchiveRetryCount(0); return row;
    }
    private AccProjectDeliverableDO root() {
        AccProjectDeliverableDO row = new AccProjectDeliverableDO();
        row.setId(20L); row.setTenantId(7L); row.setCurrentSourceVersionId(30L); row.setVersion(0); return row;
    }
    private SatisfactionResultDO result() {
        SatisfactionResultDO row = new SatisfactionResultDO();
        row.setId(40L); row.setTenantId(7L); row.setResponseId(50L); row.setArchiveActorUserId(99L);
        row.setArchiveRetryCount(0); row.setVersion(0); return row;
    }
    private ProjectDeliverableSourceAttachmentDO sourceFile(int sequence, Long artifactId, String referenceKey) {
        ProjectDeliverableSourceAttachmentDO row = new ProjectDeliverableSourceAttachmentDO();
        row.setAttachmentSequence(sequence); row.setFileArtifactId(artifactId); row.setFileVersionNo(1);
        row.setReferenceKey(referenceKey); row.setArtifactVersion(1); row.setReferenceVersion(0);
        row.setAvailabilityVersion(0); row.setScopeVersion(3L); row.setFileHash("a".repeat(64)); return row;
    }
    private SatisfactionResultFileDO resultFile(String role, int sequence, Long artifactId, String referenceKey) {
        SatisfactionResultFileDO row = new SatisfactionResultFileDO();
        row.setFileRole(role); row.setFileSequence(sequence); row.setArtifactId(artifactId); row.setVersionNo(1);
        row.setReferenceKey(referenceKey); row.setArtifactVersion(1); row.setReferenceVersion(0);
        row.setAvailabilityVersion(0); row.setScopeVersion(3L); row.setFileHash("a".repeat(64)); return row;
    }
}
