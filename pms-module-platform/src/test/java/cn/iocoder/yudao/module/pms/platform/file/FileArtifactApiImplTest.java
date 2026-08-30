package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArchiveRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService;
import cn.iocoder.yudao.module.pms.platform.service.file.GeneratedBusinessFileService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Set;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_FACT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FileArtifactApiImplTest {

    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock FileArtifactMapper artifactMapper;
    @Mock FileVersionMapper versionMapper;
    @Mock FileReferenceMapper referenceMapper;
    @Mock ExistingFileVersionAttachmentService attachmentService;
    @Mock FileArchiveRecordMapper archiveRecordMapper;
    @Mock PermissionApi permissionApi;
    @Mock GeneratedBusinessFileService generatedBusinessFileService;

    private FileArtifactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        LoginUser user = new LoginUser();
        user.setId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        api = new FileArtifactApiImpl(policyRegistry, artifactMapper, versionMapper, referenceMapper,
                attachmentService, archiveRecordMapper, permissionApi, generatedBusinessFileService);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void archivesTheCompleteAcceptanceAttachmentSetForTheFrozenActor() {
        FileReferenceSetKey attachmentKey = new FileReferenceSetKey(
                "ACC", "ACCEPTANCE_REPORT_VERSION", "900", "ACCEPTANCE_REPORT_ATTACHMENT");
        FileReferenceSetKey archiveKey = new FileReferenceSetKey(
                "ACC", "ACCEPTANCE_REPORT_VERSION", "900", "ACCEPTANCE_REPORT_ARCHIVE");
        FileReferenceDO attachment = reference();
        attachment.setOwnerContext("ACC");
        attachment.setObjectType("ACCEPTANCE_REPORT_VERSION");
        attachment.setObjectId("900");
        attachment.setPurposeCode("ACCEPTANCE_REPORT_ATTACHMENT");
        when(permissionApi.hasAnyPermissions(19L, "pms:file:archive")).thenReturn(true);
        when(policyRegistry.lockAndRevalidateReferenceSet(any())).thenReturn(policy());
        when(referenceMapper.selectSetForUpdate(any())).thenReturn(List.of(attachment), List.of());
        FileArtifactDO artifact = artifact();
        artifact.setOwnerContext("ACC");
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact);
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.insert(any())).thenAnswer(invocation -> {
            FileReferenceDO row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        });
        when(archiveRecordMapper.insert(any())).thenReturn(1);
        FileArtifactVersionFact expected = new FileArtifactVersionFact(11L, 2, "slot-a", "EVIDENCE",
                "evidence.pdf", 3L, "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 8L);

        var result = api.archiveReferenceSets(new ArchiveFileReferenceSetsCommand(
                "operation-1", "archive-1", "ACC-REPORT:900", 19L,
                attachmentKey, archiveKey, 8L, List.of(expected)));

        assertEquals("ARCHIVED", result.archivedFacts().getFirst().referenceStatus());
        assertEquals("slot-a", result.archivedFacts().getFirst().referenceKey());
        var referenceCaptor = org.mockito.ArgumentCaptor.forClass(FileReferenceDO.class);
        verify(referenceMapper).insert(referenceCaptor.capture());
        assertEquals("ACCEPTANCE_REPORT_ARCHIVE", referenceCaptor.getValue().getPurposeCode());
        assertEquals("ARCHIVED", referenceCaptor.getValue().getStatusCode());
        var recordCaptor = org.mockito.ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArchiveRecordDO.class);
        verify(archiveRecordMapper).insert(recordCaptor.capture());
        assertEquals(19L, recordCaptor.getValue().getArchivedBy());
        verify(referenceMapper, never()).updateStateIfMatch(any());
    }

    @Test
    void archiveRejectsRevokedActorBeforeFileWrites() {
        FileReferenceSetKey attachmentKey = new FileReferenceSetKey(
                "ACC", "ACCEPTANCE_REPORT_VERSION", "900", "ACCEPTANCE_REPORT_ATTACHMENT");
        FileReferenceSetKey archiveKey = new FileReferenceSetKey(
                "ACC", "ACCEPTANCE_REPORT_VERSION", "900", "ACCEPTANCE_REPORT_ARCHIVE");
        FileArtifactVersionFact expected = new FileArtifactVersionFact(11L, 2, "slot-a", "EVIDENCE",
                "evidence.pdf", 3L, "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 8L);

        assertThrows(ServiceException.class, () -> api.archiveReferenceSets(
                new ArchiveFileReferenceSetsCommand("operation-1", "archive-1", "ACC-REPORT:900",
                        19L, attachmentKey, archiveKey, 8L, List.of(expected))));

        verify(referenceMapper, never()).insert(any());
        verify(archiveRecordMapper, never()).insert(any());
    }

    @Test
    void inspectsAndRevalidatesTheSameExactReferenceFact() {
        when(policyRegistry.inspect(any())).thenReturn(policy());
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy());
        when(artifactMapper.selectOne(any())).thenReturn(artifact());
        when(versionMapper.selectOne(any())).thenReturn(version());
        when(referenceMapper.selectExact(any())).thenReturn(reference());
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.selectForUpdate(any())).thenReturn(reference());

        var inspected = api.inspect(query());
        var revalidated = api.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(
                11L, 2, "SOL", "CHANGE", "900", "EVIDENCE", "slot-a", FileActionCodes.READ,
                inspected.fileFactVersion(), inspected.scopeVersion()));

        assertEquals("slot-a", revalidated.referenceKey());
        assertEquals(new FileFactVersion(3, 4, 5), revalidated.fileFactVersion());
        assertEquals(8L, revalidated.scopeVersion());
    }

    @Test
    void rejectsChangedFrozenFileFact() {
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy());
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.selectForUpdate(any())).thenReturn(reference());

        ServiceException failure = assertThrows(ServiceException.class, () -> api.lockAndRevalidate(
                new FileArtifactVersionRevalidationQuery(11L, 2, "SOL", "CHANGE", "900", "EVIDENCE",
                        "slot-a", FileActionCodes.READ, new FileFactVersion(3, 3, 5), 8L)));

        assertEquals(FILE_FACT_VERSION_CONFLICT.getCode(), failure.getCode());
    }

    @Test
    void inspectsAndRevalidatesAnAuthorizedEmptyReferenceSet() {
        FileReferenceSetKey key = setKey();
        when(policyRegistry.inspectReferenceSet(any())).thenReturn(policy());
        when(policyRegistry.lockAndRevalidateReferenceSet(any())).thenReturn(policy());
        when(referenceMapper.selectActiveSet(any())).thenReturn(List.of());
        when(referenceMapper.selectSetForUpdate(any())).thenReturn(List.of());

        var inspected = api.inspectReferenceSets(new FileReferenceSetCollectionQuery(
                List.of(key), FileActionCodes.READ));
        var revalidated = api.lockAndRevalidateReferenceSets(
                new FileReferenceSetCollectionRevalidationQuery(List.of(
                        new FileReferenceSetExpectation(key, 8L, List.of())), FileActionCodes.READ));

        assertEquals(List.of(), inspected.getFirst().activeFacts());
        assertEquals(List.of(), revalidated.getFirst().activeFacts());
        verify(artifactMapper, never()).selectForUpdate(any());
        verify(versionMapper, never()).selectForUpdate(any());
    }

    @Test
    void inspectReferenceSetsProjectsAnInvalidatedActiveReferenceFact() {
        FileReferenceSetKey key = setKey();
        FileVersionDO invalidated = version();
        invalidated.setAvailabilityStatusCode("INVALIDATED");
        invalidated.setAvailabilityVersion(6);
        when(policyRegistry.inspectReferenceSet(any())).thenReturn(policy());
        when(referenceMapper.selectActiveSet(any())).thenReturn(List.of(reference()));
        when(artifactMapper.selectOne(any())).thenReturn(artifact());
        when(versionMapper.selectOne(any())).thenReturn(invalidated);

        var inspected = api.inspectReferenceSets(new FileReferenceSetCollectionQuery(
                List.of(key), FileActionCodes.READ));

        assertEquals("INVALIDATED", inspected.getFirst().activeFacts().getFirst().availabilityStatus());
        assertEquals(new FileFactVersion(3, 4, 6),
                inspected.getFirst().activeFacts().getFirst().fileFactVersion());
    }

    @Test
    void rejectsAnActiveMemberAddedToAnExpectedEmptySet() {
        FileReferenceSetKey key = setKey();
        when(policyRegistry.lockAndRevalidateReferenceSet(any())).thenReturn(policy());
        when(referenceMapper.selectActiveSet(any())).thenReturn(List.of(reference()));
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.selectSetForUpdate(any())).thenReturn(List.of(reference()));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> api.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(
                        List.of(new FileReferenceSetExpectation(key, 8L, List.of())), FileActionCodes.READ)));

        assertEquals(FILE_FACT_VERSION_CONFLICT.getCode(), failure.getCode());
    }

    @Test
    void lockAndRevalidateReferenceSetsStillRejectsAnInvalidatedVersion() {
        FileReferenceSetKey key = setKey();
        FileVersionDO invalidated = version();
        invalidated.setAvailabilityStatusCode("INVALIDATED");
        invalidated.setAvailabilityVersion(6);
        when(policyRegistry.lockAndRevalidateReferenceSet(any())).thenReturn(policy());
        when(referenceMapper.selectActiveSet(any())).thenReturn(List.of(reference()));
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(invalidated);
        when(referenceMapper.selectSetForUpdate(any())).thenReturn(List.of(reference()));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> api.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(
                        List.of(new FileReferenceSetExpectation(key, 8L, List.of())), FileActionCodes.READ)));

        assertEquals(FILE_VERSION_UNAVAILABLE.getCode(), failure.getCode());
    }

    @Test
    void revalidatesTheCompleteActiveSetInReferenceKeyOrder() {
        FileReferenceSetKey key = setKey();
        when(policyRegistry.lockAndRevalidateReferenceSet(any())).thenReturn(policy());
        when(referenceMapper.selectActiveSet(any())).thenReturn(List.of(reference()));
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.selectSetForUpdate(any())).thenReturn(List.of(reference()));
        FileArtifactVersionFact expected = new FileArtifactVersionFact(11L, 2, "slot-a", "EVIDENCE",
                "evidence.pdf", 3L, "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 8L);

        var result = api.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(
                List.of(new FileReferenceSetExpectation(key, 8L, List.of(expected))), FileActionCodes.READ));

        assertEquals(List.of(expected), result.getFirst().activeFacts());
    }

    private FileArtifactVersionQuery query() {
        return new FileArtifactVersionQuery(11L, 2, "SOL", "CHANGE", "900", "EVIDENCE",
                "slot-a", FileActionCodes.READ);
    }

    private FileReferenceSetKey setKey() {
        return new FileReferenceSetKey("SOL", "CHANGE", "900", "EVIDENCE");
    }

    private FileBusinessObjectPolicyFact policy() {
        return new FileBusinessObjectPolicyFact(true, 8L, "MUTABLE", "SINGLE",
                Set.of("EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }

    private FileArtifactDO artifact() {
        FileArtifactDO row = new FileArtifactDO();
        row.setId(11L);
        row.setName("evidence.pdf");
        row.setOwnerContext("SOL");
        row.setCategoryCode("EVIDENCE");
        row.setLifecycleStatusCode("ACTIVE");
        row.setVersion(3);
        return row;
    }

    private FileVersionDO version() {
        FileVersionDO row = new FileVersionDO();
        row.setArtifactId(11L);
        row.setVersionNo(2);
        row.setSizeBytes(3L);
        row.setDetectedMediaType("application/pdf");
        row.setSha256("a".repeat(64));
        row.setAvailabilityStatusCode("AVAILABLE");
        row.setAvailabilityVersion(5);
        return row;
    }

    private FileReferenceDO reference() {
        FileReferenceDO row = new FileReferenceDO();
        row.setId(21L);
        row.setReferenceKey("slot-a");
        row.setArtifactId(11L);
        row.setFileVersionNo(2);
        row.setStatusCode("ACTIVE");
        row.setScopeVersion(8L);
        row.setVersion(4);
        return row;
    }
}
