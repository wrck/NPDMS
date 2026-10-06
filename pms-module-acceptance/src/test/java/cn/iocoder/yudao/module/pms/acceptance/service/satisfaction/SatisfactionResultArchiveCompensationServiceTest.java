package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultFileMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionResultArchiveProjectionUpdate;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionResultFilesQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 满意度投影材料归档补偿（P06R I2）：材料行是补偿主体，经 材料 → 提交台账 →
 * satisfaction-result:{成果ID}:{版本} 反查来源；材料与成果文件以（工件、版本、摘要）集合互证。
 */
class SatisfactionResultArchiveCompensationServiceTest {

    final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    final SatisfactionResultMapper resultMapper = mock(SatisfactionResultMapper.class);
    final SatisfactionResultFileMapper resultFileMapper = mock(SatisfactionResultFileMapper.class);
    final FileArtifactApi fileApi = mock(FileArtifactApi.class);
    final SatisfactionResultArchiveCompensationService service =
            new SatisfactionResultArchiveCompensationService(platform, resultMapper, resultFileMapper, fileApi);

    @Test
    void archivesDocumentSignatureAndAttachmentGroupsThenClosesMaterialAndProjection() {
        stubProjection();
        when(resultMapper.selectByIdForUpdate(7L, 40L)).thenReturn(result());
        when(platform.lockMaterials(List.of(30L, 31L, 32L))).thenReturn(List.of(
                material(30L, 100L), material(31L, 101L), material(32L, 102L)));
        when(resultFileMapper.selectListByResult(new SatisfactionResultFilesQuery(7L, 40L))).thenReturn(List.of(
                resultFile("RESULT_DOCUMENT", 1, 100L, "doc"),
                resultFile("SIGNATURE", 1, 101L, "sig"),
                resultFile("ATTACHMENT", 1, 102L, "att")));
        when(resultMapper.updateArchiveProjection(any())).thenReturn(1);

        service.archive(7L, 30L);

        var commands = ArgumentCaptor.forClass(ArchiveFileReferenceSetsCommand.class);
        verify(fileApi, times(3)).archiveReferenceSets(commands.capture());
        assertEquals(List.of("SATISFACTION_RESULT_DOCUMENT", "SATISFACTION_SIGNATURE", "SATISFACTION_ATTACHMENT"),
                commands.getAllValues().stream().map(value -> value.attachmentSetKey().purposeCode()).toList());
        assertEquals(List.of("40", "50", "50"), commands.getAllValues().stream()
                .map(value -> value.attachmentSetKey().objectId()).toList());
        assertEquals(3L, commands.getAllValues().getFirst().expectedScopeVersion());
        verify(platform, times(3)).markMaterialArchiveState(any(), eq(PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED),
                any(), any(), eq("99"));
        var update = ArgumentCaptor.forClass(SatisfactionResultArchiveProjectionUpdate.class);
        verify(resultMapper).updateArchiveProjection(update.capture());
        assertEquals("ARCHIVED", update.getValue().archiveStatus());
        assertEquals(1001L, update.getValue().deliverableSourceVersionId());
    }

    @Test
    void alreadyArchivedMaterialIsANoOpAndConflictingStatesOrSourcesAreRejected() {
        when(platform.lockMaterials(List.of(30L))).thenReturn(List.of(material(30L, 100L,
                PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED)));
        service.archive(7L, 30L);
        verify(fileApi, never()).archiveReferenceSets(any());

        when(platform.lockMaterials(List.of(30L))).thenReturn(List.of(material(30L, 100L, "INVALID")));
        assertEquals("archive source state conflict", assertThrows(IllegalStateException.class,
                () -> service.archive(7L, 30L)).getMessage());

        when(platform.lockMaterials(List.of(30L))).thenReturn(List.of(material(30L, 100L,
                PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION)));
        when(platform.findSubmissionIdByMaterial(30L)).thenReturn(Optional.empty());
        assertEquals("archive source identity conflict", assertThrows(IllegalStateException.class,
                () -> service.archive(7L, 30L)).getMessage());

        when(platform.findSubmissionIdByMaterial(30L)).thenReturn(Optional.of(1001L));
        when(platform.findSubmissionById(1001L)).thenReturn(Optional.of(new TemplateFrozenSubmissionView(
                1001L, 20L, "report:55", "UPLOAD", "CURRENT", "{}", null, List.of(30L), null)));
        assertEquals("archive source identity conflict", assertThrows(IllegalStateException.class,
                () -> service.archive(7L, 30L)).getMessage());
        verify(fileApi, never()).archiveReferenceSets(any());
    }

    @Test
    void fileSetMismatchBetweenMaterialAndOwnerResultIsRejected() {
        stubProjection();
        when(resultMapper.selectByIdForUpdate(7L, 40L)).thenReturn(result());
        when(platform.lockMaterials(List.of(30L, 31L, 32L))).thenReturn(List.of(
                material(30L, 100L), material(31L, 101L), material(32L, 103L)));
        when(resultFileMapper.selectListByResult(new SatisfactionResultFilesQuery(7L, 40L))).thenReturn(List.of(
                resultFile("RESULT_DOCUMENT", 1, 100L, "doc"),
                resultFile("SIGNATURE", 1, 101L, "sig"),
                resultFile("ATTACHMENT", 1, 102L, "att")));

        assertEquals("archive source file conflict", assertThrows(IllegalStateException.class,
                () -> service.archive(7L, 30L)).getMessage());
        verify(fileApi, never()).archiveReferenceSets(any());
    }

    @Test
    void recordFailureBumpsRetryAndMirrorsPendingProjectionState() {
        when(platform.bumpMaterialArchiveRetry(30L, "ARCHIVE_TIMEOUT")).thenReturn(1);
        when(platform.findSubmissionIdByMaterial(30L)).thenReturn(Optional.of(1001L));
        when(platform.findSubmissionById(1001L)).thenReturn(Optional.of(submission()));
        when(resultMapper.selectByIdForUpdate(7L, 40L)).thenReturn(result());
        when(resultMapper.updateArchiveProjection(any())).thenReturn(1);

        service.recordFailure(7L, 30L, "ARCHIVE_TIMEOUT");

        var update = ArgumentCaptor.forClass(SatisfactionResultArchiveProjectionUpdate.class);
        verify(resultMapper).updateArchiveProjection(update.capture());
        assertEquals(PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION, update.getValue().archiveStatus());
        assertEquals("ARCHIVE_TIMEOUT", update.getValue().archiveFailureCode());
        assertEquals(1, update.getValue().archiveRetryCount());

        when(platform.bumpMaterialArchiveRetry(30L, "ARCHIVE_TIMEOUT")).thenReturn(0);
        service.recordFailure(7L, 30L, "ARCHIVE_TIMEOUT");
        verify(resultMapper, org.mockito.Mockito.times(1)).updateArchiveProjection(any());
    }

    @Test
    void sharedArchivedMaterialStillArchivesEachNativeSubmissionTarget() {
        when(platform.lockMaterials(List.of(30L,31L,32L))).thenReturn(List.of(
                material(30L,100L,"ARCHIVED"),material(31L,101L,"ARCHIVED"),material(32L,102L,"ARCHIVED")));
        when(platform.markSubmissionArchiveState(any(),eq("ARCHIVED"),any())).thenReturn(true);
        when(resultMapper.updateArchiveProjection(any())).thenReturn(1);
        for(long id:List.of(40L,41L)) {
            long submissionId=1000L+id;
            when(platform.lockPendingArchiveSubmission(submissionId)).thenReturn(Optional.of(
                    new TemplateFrozenSubmissionView(submissionId,20L,"satisfaction-result:"+id+":1","AUTO_PROJECTION","CURRENT","{}",null,List.of(30L,31L,32L),null)));
            var owner=result();owner.setId(id);when(resultMapper.selectByIdForUpdate(7L,id)).thenReturn(owner);
            when(resultFileMapper.selectListByResult(new SatisfactionResultFilesQuery(7L,id))).thenReturn(List.of(
                    resultFile("RESULT_DOCUMENT",1,100L,"doc"),resultFile("SIGNATURE",1,101L,"sig"),resultFile("ATTACHMENT",1,102L,"att")));
            service.archiveSubmission(7L,submissionId);
            verify(platform).markSubmissionArchiveState(submissionId,"ARCHIVED",null);
        }
        var commands=ArgumentCaptor.forClass(ArchiveFileReferenceSetsCommand.class);
        verify(fileApi,times(6)).archiveReferenceSets(commands.capture());
        assertEquals(List.of("40","40","40","41","41","41"),commands.getAllValues().stream()
                .map(command->command.archiveSetKey().objectId()).toList());
        verify(platform,never()).findSubmissionIdByMaterial(any());
    }

    @Test void scopedFailureUpdatesOnlyItsOwnNativeResult() {
        when(platform.lockPendingArchiveSubmission(1001L)).thenReturn(Optional.of(submission()));
        when(platform.markSubmissionArchiveState(1001L,"PENDING_COMPENSATION","ARCHIVE_TIMEOUT")).thenReturn(true);
        when(resultMapper.selectByIdForUpdate(7L,40L)).thenReturn(result());
        when(resultMapper.updateArchiveProjection(any())).thenReturn(1);
        service.recordSubmissionFailure(7L,1001L,"ARCHIVE_TIMEOUT");
        var update=ArgumentCaptor.forClass(SatisfactionResultArchiveProjectionUpdate.class);
        verify(resultMapper).updateArchiveProjection(update.capture());
        assertEquals(40L,update.getValue().resultId());assertEquals(1,update.getValue().archiveRetryCount());
        verify(platform,never()).findSubmissionIdByMaterial(any());
    }

    private void stubProjection() {
        when(platform.lockMaterials(anyList())).thenAnswer(
                invocation -> ((List<Long>) invocation.getArgument(0)).stream()
                        .map(id -> material(id, id + 70L)).toList());
        when(platform.findSubmissionIdByMaterial(30L)).thenReturn(Optional.of(1001L));
        when(platform.findSubmissionById(1001L)).thenReturn(Optional.of(submission()));
    }

    private TemplateFrozenSubmissionView submission() {
        return new TemplateFrozenSubmissionView(1001L, 20L, "satisfaction-result:40:1",
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", "{}", null,
                List.of(30L, 31L, 32L), null);
    }

    private TemplateFrozenMaterialView material(Long id, Long artifactId) {
        return material(id, artifactId, PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION);
    }

    private TemplateFrozenMaterialView material(Long id, Long artifactId, String archiveStatus) {
        return new TemplateFrozenMaterialView(id, 20L, "FILE", null, artifactId, 1,
                "a".repeat(64), "report.pdf", null, null, null, "ACTIVE", archiveStatus, null, 0);
    }

    private SatisfactionResultDO result() {
        SatisfactionResultDO row = new SatisfactionResultDO();
        row.setId(40L);
        row.setTenantId(7L);
        row.setResponseId(50L);
        row.setArchiveActorUserId(99L);
        row.setArchiveRetryCount(0);
        row.setVersion(0);
        return row;
    }

    private SatisfactionResultFileDO resultFile(String role, int sequence, Long artifactId, String referenceKey) {
        SatisfactionResultFileDO row = new SatisfactionResultFileDO();
        row.setFileRole(role);
        row.setFileSequence(sequence);
        row.setArtifactId(artifactId);
        row.setVersionNo(1);
        row.setReferenceKey(referenceKey);
        row.setArtifactVersion(1);
        row.setReferenceVersion(0);
        row.setAvailabilityVersion(0);
        row.setScopeVersion(3L);
        row.setFileHash("a".repeat(64));
        return row;
    }
}
