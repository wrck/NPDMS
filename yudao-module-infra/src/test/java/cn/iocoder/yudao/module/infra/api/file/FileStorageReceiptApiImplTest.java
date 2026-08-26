package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageAccessReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageReceiptApiImplTest {

    @Mock private FileConfigService fileConfigService;
    @Mock private FileMapper fileMapper;
    @Mock private FileClient masterClient;
    @Mock private FileClient frozenClient;

    private FileStorageReceiptApiImpl api;

    @BeforeEach
    void setUp() {
        api = new FileStorageReceiptApiImpl();
        ReflectionTestUtils.setField(api, "fileConfigService", fileConfigService);
        ReflectionTestUtils.setField(api, "fileMapper", fileMapper);
    }

    @Test
    void storesOnceOnTheCurrentMasterAndReturnsTheTechnicalReceipt() throws Exception {
        when(fileMapper.selectListByStorageOperation(any())).thenReturn(List.of());
        when(fileConfigService.getMasterFileClient()).thenReturn(masterClient);
        when(masterClient.getId()).thenReturn(11L);
        when(masterClient.upload(any(), eq("pms-storage-receipts/op-101"), eq("application/pdf")))
                .thenReturn("https://private/files/op-101");
        when(fileMapper.insert(any(FileDO.class))).thenAnswer(invocation -> {
            FileDO file = invocation.getArgument(0);
            file.setId(101L);
            return 1;
        });

        FileStorageReceipt receipt = api.store(command("op-101", "evidence.pdf", "application/pdf"));

        assertEquals("op-101", receipt.storageOperationId());
        assertEquals(101L, receipt.infraFileId());
        assertEquals("evidence.pdf", receipt.name());
        assertEquals(3L, receipt.sizeBytes());
        verify(masterClient).upload(any(), eq("pms-storage-receipts/op-101"), eq("application/pdf"));
    }

    @Test
    void replaysTheFrozenRecordWithoutReadingTheCurrentMaster() {
        FileDO existing = file(201L, 21L, "op-201", "evidence.pdf", "application/pdf", 3L);
        when(fileMapper.selectListByStorageOperation(any())).thenReturn(List.of(existing));

        FileStorageReceipt receipt = api.store(command("op-201", "evidence.pdf", "application/pdf"));

        assertEquals(201L, receipt.infraFileId());
        verify(fileConfigService, never()).getMasterFileClient();
        verify(fileMapper, never()).insert(any(FileDO.class));
    }

    @Test
    void rejectsAReplayWithDifferentReceiptFacts() {
        FileDO existing = file(202L, 21L, "op-202", "other.pdf", "application/pdf", 3L);
        when(fileMapper.selectListByStorageOperation(any())).thenReturn(List.of(existing));

        assertThrows(ServiceException.class,
                () -> api.store(command("op-202", "evidence.pdf", "application/pdf")));
        verify(fileConfigService, never()).getMasterFileClient();
    }

    @Test
    void failsClosedWhenTheReservedPathHasMultipleRecords() {
        when(fileMapper.selectListByStorageOperation(any())).thenReturn(List.of(
                file(301L, 31L, "op-301", "a.pdf", "application/pdf", 3L),
                file(302L, 32L, "op-301", "a.pdf", "application/pdf", 3L)));

        assertThrows(ServiceException.class,
                () -> api.store(command("op-301", "a.pdf", "application/pdf")));
        verify(fileConfigService, never()).getMasterFileClient();
    }

    @Test
    void deletesUsingTheConfigFrozenOnTheReceiptAndTreatsMissingAsComplete() throws Exception {
        FileDO existing = file(401L, 41L, "op-401", "a.pdf", "application/pdf", 3L);
        when(fileMapper.selectListByStorageOperation(any()))
                .thenReturn(List.of(existing), List.of());
        when(fileConfigService.getFileClient(41L)).thenReturn(frozenClient);

        api.delete("op-401");
        api.delete("op-401");

        verify(frozenClient).delete("pms-storage-receipts/op-401");
        verify(fileMapper).deleteById(401L);
        verify(fileConfigService, never()).getMasterFileClient();
    }

    @Test
    void presignsUsingTheConfigAndPathFrozenOnTheReceipt() {
        FileDO existing = file(501L, 51L, "op-501", "a.pdf", "application/pdf", 3L);
        when(fileMapper.selectById(501L)).thenReturn(existing);
        when(fileConfigService.getFileClient(51L)).thenReturn(frozenClient);
        when(frozenClient.presignGetUrl("pms-storage-receipts/op-501", 60))
                .thenReturn("https://private/signed");
        LocalDateTime before = LocalDateTime.now().plusSeconds(59);

        FileStorageAccessReceipt receipt = api.presignGet(501L, 60);

        assertEquals("https://private/signed", receipt.shortLivedUrl());
        assertTrue(receipt.expiresAt().isAfter(before));
        verify(fileConfigService, never()).getMasterFileClient();
    }

    @Test
    void removesTheUploadedObjectWhenReceiptPersistenceFails() throws Exception {
        when(fileMapper.selectListByStorageOperation(any())).thenReturn(List.of());
        when(fileConfigService.getMasterFileClient()).thenReturn(masterClient);
        when(masterClient.getId()).thenReturn(61L);
        when(masterClient.upload(any(), eq("pms-storage-receipts/op-601"), any()))
                .thenReturn("https://private/files/op-601");
        RuntimeException persistenceFailure = new IllegalStateException("insert failed");
        doThrow(persistenceFailure).when(fileMapper).insert(any(FileDO.class));

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> api.store(command("op-601", "a.pdf", "application/pdf")));

        assertEquals(persistenceFailure, actual);
        verify(masterClient).delete("pms-storage-receipts/op-601");
    }

    @Test
    void rejectsContentOverTheApprovedLimitBeforeCallingStorage() {
        byte[] content = new byte[FileStorageReceiptApiImpl.MAX_CONTENT_BYTES + 1];

        assertThrows(ServiceException.class, () -> api.store(
                new FileStorageStoreCommand("op-701", content, "a.pdf", "application/pdf")));

        verify(fileMapper, never()).selectListByStorageOperation(any());
        verify(fileConfigService, never()).getMasterFileClient();
    }

    private FileStorageStoreCommand command(String operationId, String name, String mediaType) {
        return new FileStorageStoreCommand(operationId, new byte[]{1, 2, 3}, name, mediaType);
    }

    private FileDO file(long id, long configId, String operationId, String name,
                        String mediaType, long size) {
        return new FileDO().setId(id).setConfigId(configId).setName(name)
                .setPath(FileStorageReceiptApiImpl.buildStoragePath(operationId))
                .setUrl("https://private/" + operationId).setType(mediaType).setSize(size);
    }

}
