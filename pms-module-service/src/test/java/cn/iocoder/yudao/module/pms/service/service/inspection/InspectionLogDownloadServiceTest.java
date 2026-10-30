package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.pms.platform.api.file.AuthenticatedFileContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionParserDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionLogDownloadServiceTest {
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionTaskDeviceMapper devices = mock(InspectionTaskDeviceMapper.class);
    private final AuthenticatedFileContentApi content = mock(AuthenticatedFileContentApi.class);
    private final InspectionRequestContext context = new InspectionRequestContext(1L, 2L, null, "download", null);
    private final InspectionLogDownloadService service = new InspectionLogDownloadService(access, executions, devices, content);
    private InspectionTaskDO task;
    private InspectionExecutionDO execution;
    private InspectionTaskDeviceDO device;
    private byte[] original;

    @BeforeEach void prepare() {
        task = new InspectionTaskDO(); task.setId(10L); task.setTenantId(1L); task.setCurrentAttemptId(20L); task.setStatusCode("ARCHIVED");
        execution = new InspectionExecutionDO(); execution.setId(40L); execution.setTenantId(1L); execution.setTaskId(10L);
        execution.setAttemptId(19L); execution.setDeviceId(30L); execution.setArtifactId(50L); execution.setFileVersionNo(2); execution.setFileReferenceKey("precise-log-version");
        device = new InspectionTaskDeviceDO(); device.setTenantId(1L); device.setTaskId(10L); device.setDeviceId(30L); device.setSerialNumber("SN-A");
        original = InspectionParserDocuments.encode(List.of(
                new InspectionParserDocuments.Block(2, "show first", "SUCCEEDED", "line1\r\n[REDACTED]\u0004", "", false, 0),
                new InspectionParserDocuments.Block(1, "", "FAILED", "", "second error\n", true, 1)));
        execution.setFileSha256(DigestUtil.sha256Hex(original));
        when(access.task(any())).thenReturn(task); when(executions.selectById(40L)).thenReturn(execution);
        when(devices.selectForTask(any())).thenReturn(List.of(device)); when(content.read(any())).thenReturn(original);
    }

    @Test void downloadsCompleteUtf8TextWithHonestProvenanceAndPreservesBlockOrderAndEmptyFields() {
        var result = service.download(new InspectionLogDownloadService.Query(context, 10L, 40L));
        String text = new String(result.content(), StandardCharsets.UTF_8);
        assertTrue(text.contains("脱敏日志文本副本")); assertTrue(text.contains("非上传原文件"));
        assertTrue(text.contains("show first")); assertTrue(text.contains("line1\r\n[REDACTED]\u0004"));
        assertTrue(text.contains("[stderr]\r\n\r\n")); assertTrue(text.contains("[stdout]\r\n\r\n"));
        assertTrue(text.indexOf("[command 2]") < text.indexOf("[command 1]"));
        assertTrue(text.endsWith("second error\n\r\n")); assertFalse(text.startsWith("{"));
        assertEquals("inspection-10-40-SN-A-redacted.txt", result.fileName());
        assertArrayEquals(InspectionParserDocuments.encode(List.of(
                new InspectionParserDocuments.Block(2, "show first", "SUCCEEDED", "line1\r\n[REDACTED]\u0004", "", false, 0),
                new InspectionParserDocuments.Block(1, "", "FAILED", "", "second error\n", true, 1))), original);
        var query = ArgumentCaptor.forClass(AuthenticatedFileContentApi.ReadCommand.class); verify(content).read(query.capture());
        assertEquals(50L, query.getValue().file().artifactId()); assertEquals(2, query.getValue().file().versionNo());
        assertEquals("10", query.getValue().file().objectId()); assertEquals("LOG", query.getValue().file().purposeCode());
        assertEquals("precise-log-version", query.getValue().file().referenceKey()); assertEquals(FileActionCodes.DOWNLOAD, query.getValue().file().requiredAction());
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
    }

    @ParameterizedTest @CsvSource({"2,10", "1,11"})
    void rejectsForeignTenantOrTaskBeforeReadingFile(long tenant, long taskId) {
        execution.setTenantId(tenant); execution.setTaskId(taskId);
        assertThrows(InspectionException.class, () -> service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)));
        verifyNoInteractions(content);
    }

    @Test void cannotDownloadAnotherDeviceLogEvenWhenItsFileIsValid() {
        execution.setDeviceId(31L);
        assertThrows(InspectionException.class, () -> service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)));
        verifyNoInteractions(content);
    }

    @Test void propagatesFilePermissionOrAvailabilityDenialWithoutProducingACopy() {
        when(content.read(any())).thenThrow(new IllegalArgumentException("denied"));
        assertThrows(IllegalArgumentException.class, () -> service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
    }

    @Test void rejectsChangedFileContentInsteadOfExportingAMismatchedVersion() {
        when(content.read(any())).thenReturn("changed".getBytes(StandardCharsets.UTF_8));
        assertThrows(InspectionException.class, () -> service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)));
    }

    @Test void rejectsIncompleteSavedBlocksInsteadOfSilentlyOmittingOutput() {
        byte[] incomplete = InspectionParserDocuments.encode(List.of(new InspectionParserDocuments.Block(1, "show", "SUCCEEDED", "output", null, false, 0)));
        execution.setFileSha256(DigestUtil.sha256Hex(incomplete)); when(content.read(any())).thenReturn(incomplete);
        assertThrows(InspectionException.class, () -> service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)));
    }

    @Test void neverMergesTwoDevicesFromTheSameOriginalBatchIntoOneDownload() {
        var second = new InspectionExecutionDO(); second.setId(41L); second.setTenantId(1L); second.setTaskId(10L);
        second.setDeviceId(31L); second.setArtifactId(51L); second.setFileVersionNo(1); second.setFileReferenceKey("second-device-log");
        var secondDevice = new InspectionTaskDeviceDO(); secondDevice.setTenantId(1L); secondDevice.setTaskId(10L);
        secondDevice.setDeviceId(31L); secondDevice.setSerialNumber("SN-B");
        byte[] secondBytes = InspectionParserDocuments.encode(List.of(new InspectionParserDocuments.Block(1, "show second", "SUCCEEDED", "device B only", "", false, 0)));
        second.setFileSha256(DigestUtil.sha256Hex(secondBytes));
        when(executions.selectById(41L)).thenReturn(second); when(devices.selectForTask(any())).thenReturn(List.of(device, secondDevice));
        when(content.read(any())).thenAnswer(call -> ((AuthenticatedFileContentApi.ReadCommand) call.getArgument(0)).file().artifactId().equals(50L) ? original : secondBytes);
        String firstText = new String(service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)).content(), StandardCharsets.UTF_8);
        var secondDownload = service.download(new InspectionLogDownloadService.Query(context, 10L, 41L));
        String secondText = new String(secondDownload.content(), StandardCharsets.UTF_8);
        assertTrue(firstText.contains("show first")); assertFalse(firstText.contains("device B only"));
        assertTrue(secondText.contains("device B only")); assertFalse(secondText.contains("show first"));
        assertEquals("inspection-10-41-SN-B-redacted.txt", secondDownload.fileName());
    }

    @Test void reusesTaskAccessBeforeAnyFileLookupAndDoesNotBypassDeniedProjectScope() {
        when(access.task(any())).thenThrow(new InspectionException(InspectionException.Reason.FORBIDDEN, "denied"));
        assertThrows(InspectionException.class, () -> service.download(new InspectionLogDownloadService.Query(context, 10L, 40L)));
        verifyNoInteractions(executions, devices, content);
    }

    @Test void sanitizesFilenameCharactersWithoutChangingTheSavedLog() {
        device.setSerialNumber("SN/..\\\r\n\"乙");
        var result = service.download(new InspectionLogDownloadService.Query(context, 10L, 40L));
        assertTrue(result.fileName().endsWith("-redacted.txt"));
        assertFalse(result.fileName().contains("/")); assertFalse(result.fileName().contains("\\"));
        assertFalse(result.fileName().contains("\r")); assertFalse(result.fileName().contains("\n")); assertFalse(result.fileName().contains("\""));
    }
}
