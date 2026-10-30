package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InspectionUmcArchiveTest {
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final InspectionTaskDeviceMapper devices = mock(InspectionTaskDeviceMapper.class);
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionAttemptMapper attempts = mock(InspectionAttemptMapper.class);
    private final InspectionReportRevisionMapper reports = mock(InspectionReportRevisionMapper.class);
    private final InspectionReportFileMapper files = mock(InspectionReportFileMapper.class);
    private final NativeGeneratedFileApi generated = mock(NativeGeneratedFileApi.class);
    private final FileEvidenceApi evidence = mock(FileEvidenceApi.class);
    private final AuthenticatedFileContentApi content = mock(AuthenticatedFileContentApi.class);
    private final InspectionTaskDO task = new InspectionTaskDO();
    private final InspectionReportRevisionDO report = new InspectionReportRevisionDO();
    private final InspectionRequestContext context = new InspectionRequestContext(7L, 9L, "test", "test", 1L);
    private final InspectionUmcTransactions.ReportAccess request = new InspectionUmcTransactions.ReportAccess(context, 10L, 20L);
    private InspectionUmcTransactions transactions;
    private static final byte[] JSON = "{\"id\":\"690\",\"taskid\":\"75\",\"devlist\":[{\"sn\":\"TEST-SN\",\"deviceip\":\"192.0.2.10\",\"rulelist\":[]}]}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach void setUp() {
        transactions = new InspectionUmcTransactions(access, devices, attempts, executions,
                reports, files, generated, evidence, content);
        task.setId(10L); task.setTenantId(7L); task.setTaskCode("INS10"); task.setVersion(1L); task.setStatusCode("EXECUTING"); task.setCurrentAttemptId(30L);
        report.setId(20L); report.setTenantId(7L); report.setTaskId(10L); report.setAttemptId(30L);
        report.setSourceCode("UMC"); report.setStatusCode("AVAILABLE"); report.setRevisionNo(1);
        when(access.task(any())).thenReturn(task);
        when(reports.selectContent(any())).thenReturn(report);
        report.setResultJson(JsonUtils.toJsonString(JsonUtils.parseObject(JSON, UmcInspectionApi.Report.class)));
        report.setResultSha256(DigestUtil.sha256Hex(report.getResultJson()));
        report.setSourceContextJson(JsonUtils.toJsonString(new InspectionUmcReportResults.Source(40L, "192.0.2.10", "TEST-SN", true, null,
                new InspectionUmcReportResults.StoredLog(50L, 2, "stored-input", "a".repeat(64)))));
    }

    @Test void listAndDetailUseLocalBusinessDataWithoutFileParsingOrUmc() {
        var row = file("DOC", "doc".getBytes(StandardCharsets.UTF_8));
        when(reports.selectForTask(any())).thenReturn(List.of(report));
        when(files.selectForTask(any())).thenReturn(List.of(row));
        assertNull(transactions.list(new InspectionTaskQueryService.Detail(context, 10L)).getFirst().detail());
        verifyNoInteractions(content);
        var result = transactions.detail(request);
        assertEquals("690", result.detail().id());
        assertEquals("TEST-SN", result.detail().devlist().getFirst().sn());
        verify(reports).selectContent(argThat(query -> query.tenantId().equals(7L) && query.taskId().equals(10L) && query.reportId().equals(20L)));
        verifyNoInteractions(content, generated, evidence);
        assertEquals("EXECUTING", task.getStatusCode()); assertNull(task.getCurrentReportId());
    }

    @Test void completedRetryReturnsLocalSnapshotWithoutAnyRemoteOrWrite() {
        var tx = mock(InspectionUmcTransactions.class);
        var umc = mock(UmcInspectionApi.class);
        var parser = mock(DeviceLogParsingApi.class);
        var commands = mock(InspectionCommandExecutor.class);
        var saved = new InspectionUmcTransactions.View(20L, 30L, 1, "AVAILABLE", null, null, List.of(),
                JsonUtils.parseObject(JSON, UmcInspectionApi.Report.class));
        when(tx.detail(request)).thenReturn(saved);
        var service = new InspectionUmcService(tx, commands, umc);
        assertSame(saved, service.run(request));
        verify(tx).detail(request); verifyNoMoreInteractions(tx);
        verifyNoInteractions(umc, parser, commands);
    }

    @Test void missingLocalDataFailsWithoutFileOrRemoteFallback() {
        when(files.selectForTask(any())).thenReturn(List.of());
        report.setResultJson(null);
        assertThrows(InspectionException.class, () -> transactions.detail(request));
        verifyNoInteractions(content, generated);
    }

    @Test void failedOrPendingRecordIsNotReadAsACompletedResult() {
        report.setStatusCode("FAILED");
        when(files.selectForTask(any())).thenReturn(List.of());
        assertNull(transactions.detail(request).detail());
        verifyNoInteractions(content, generated);
    }

    @Test void recoveredReportDetailsSuppressOnlyObsoleteFailuresWithoutChangingStoredHistory() {
        String previous = "UMC处理或保存失败（NOT_CONFIGURED）";
        report.setFailureMessage(previous);
        assertNull(transactions.detail(request).failureMessage());
        assertEquals(previous, report.getFailureMessage());
        report.setStatusCode("GENERATING");
        assertEquals(previous, transactions.detail(request).failureMessage());
        verifyNoInteractions(content, generated, evidence);
    }

    @Test void optimisticWriteFailureCannotReturnSuccessAndOnlyDocUsesFiles() {
        report.setStatusCode("GENERATING");
        report.setResultJson(null); report.setResultSha256(null);
        when(files.selectForTask(any())).thenReturn(List.of());
        var registered = new NativeGeneratedFileApi.RegisteredFile(60L, 70L, 80L, 1, "a".repeat(64), "report.doc");
        when(generated.create(any())).thenReturn(registered);
        when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(0);
        when(evidence.inspectDocument(7L, 70L)).thenReturn(new FileEvidenceApi.Document(70L, "SRV", "INSPECTION_TASK", "10",
                "UMC_REPORT", "stored-DOC", 80L, 1, "a".repeat(64), "report.doc", true));
        byte[] doc = "test-doc".getBytes(StandardCharsets.UTF_8);
        var result = new UmcInspectionApi.Result(new UmcInspectionApi.Checkpoint("input", "75", "690", "ACCEPTED"), "report.doc",
                "application/vnd.ms-wordml", DigestUtil.sha256Hex(doc), doc, DigestUtil.sha256Hex(JSON), JSON,
                JsonUtils.parseObject(JSON, UmcInspectionApi.Report.class), List.of());
        assertThrows(InspectionException.class, () -> transactions.complete(new InspectionUmcTransactions.Complete(request, result)));
        verify(reports).updateById(any(InspectionReportRevisionDO.class));
        verify(generated).create(argThat(command -> command.fileName().equals("INS10-巡检报告-1.doc")));
        verifyNoMoreInteractions(generated); verifyNoInteractions(content);
        assertNull(task.getCurrentReportId());
    }

    @Test void failedRequestKeepsPendingReportMutableForRecovery() {
        report.setStatusCode("GENERATING"); report.setResultJson(null); report.setResultSha256(null);
        when(files.selectForTask(any())).thenReturn(List.of());
        when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        var checkpoint = new UmcInspectionApi.Checkpoint("input", "75", "690", "UNKNOWN");
        var failed = transactions.fail(new InspectionUmcTransactions.Failed(request, checkpoint, "TIMEOUT"));
        assertEquals("GENERATING", failed.statusCode()); assertNotNull(failed.failureMessage());
        assertEquals(checkpoint, InspectionUmcReportResults.source(report).checkpoint());
        assertNull(report.getResultJson()); verifyNoInteractions(generated, content);
    }

    @Test void networkFailureBeforeUploadCanResumeTheSameIntent() {
        report.setStatusCode("GENERATING"); report.setResultJson(null); report.setResultSha256(null);
        when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        var checkpoint = new UmcInspectionApi.Checkpoint("input", "75", null, "NOT_SENT");
        transactions.fail(new InspectionUmcTransactions.Failed(request, checkpoint, "REQUEST_FAILED"));
        var source = InspectionUmcReportResults.source(report);
        assertFalse(source.dispatched(), "A confirmed pre-upload failure must permit the original intent to upload on retry");
        assertEquals(checkpoint, source.checkpoint());
    }

    @Test void ambiguousAndAcceptedUploadsNeverBecomeSafeToResubmit() {
        for (var state : List.of("UNKNOWN", "ACCEPTED")) {
            report.setStatusCode("GENERATING"); report.setResultJson(null); report.setResultSha256(null);
            when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
            var checkpoint = new UmcInspectionApi.Checkpoint("input", "75", null, state);
            transactions.fail(new InspectionUmcTransactions.Failed(request, checkpoint, "REQUEST_FAILED"));
            assertTrue(InspectionUmcReportResults.source(report).dispatched());
            assertEquals(checkpoint, InspectionUmcReportResults.source(report).checkpoint());
        }
    }

    @Test void processStartFailureIsRetryableButProcessLossWithoutCheckpointRemainsAmbiguous() {
        report.setStatusCode("GENERATING"); report.setResultJson(null); report.setResultSha256(null);
        when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        transactions.fail(new InspectionUmcTransactions.Failed(request, null, "WORKER_START_FAILED"));
        assertFalse(InspectionUmcReportResults.source(report).dispatched());
        transactions.fail(new InspectionUmcTransactions.Failed(request, null, "PROCESS_TIMEOUT"));
        assertTrue(InspectionUmcReportResults.source(report).dispatched());
    }

    @Test void unknownTaskCreationCannotCreateASecondTaskEvenBeforeLogUpload() {
        report.setStatusCode("GENERATING"); report.setResultJson(null); report.setResultSha256(null);
        when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        var checkpoint = new UmcInspectionApi.Checkpoint("input", null, null, "NOT_SENT");
        transactions.fail(new InspectionUmcTransactions.Failed(request, checkpoint, "TASK_CREATION_OUTCOME_UNKNOWN"));
        assertTrue(InspectionUmcReportResults.source(report).dispatched());
    }

    @Test void inputLogIsReferencedByOriginalReportInsteadOfBecomingAReportFileFormat() {
        var attempt = new InspectionAttemptDO(); attempt.setId(30L); attempt.setTenantId(7L); attempt.setTaskId(10L); attempt.setMode("OFFLINE");
        var device = new InspectionTaskDeviceDO(); device.setDeviceId(40L); device.setSerialNumber("TEST-SN");
        when(attempts.selectById(30L)).thenReturn(attempt); when(devices.selectForTask(any())).thenReturn(List.of(device));
        when(reports.insert(any(InspectionReportRevisionDO.class))).thenAnswer(call -> { call.<InspectionReportRevisionDO>getArgument(0).setId(20L); return 1; });
        when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        byte[] normalized = cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionParserDocuments.encode(List.of(
                new cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionParserDocuments.Block(1, "show version", "OK", "SN TEST-SN", "", false, 0)));
        var execution = new InspectionExecutionDO(); execution.setId(90L); execution.setTenantId(7L); execution.setTaskId(10L); execution.setAttemptId(30L);
        execution.setDeviceId(40L); execution.setPurposeCode("OFFLINE"); execution.setStatusCode("PENDING"); execution.setOriginalFileSha256("b".repeat(64));
        execution.setRecognizedSerial("TEST-SN"); execution.setArtifactId(80L); execution.setFileVersionNo(1); execution.setFileReferenceKey("input-ref");
        execution.setFileSha256(DigestUtil.sha256Hex(normalized)); execution.setCommandHint("show version");
        var otherHint = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(execution, InspectionExecutionDO.class);
        otherHint.setId(91L); otherHint.setCommandHint(null);
        when(executions.selectForTask(any())).thenReturn(List.of(otherHint, execution));
        when(content.read(any())).thenReturn(normalized);
        var reference = transactions.prepare(new InspectionUmcTransactions.Prepare(new InspectionUmcTransactions.Access(context, 10L, 40L),
                "192.0.2.10", "b".repeat(64), "show version"));
        assertEquals(20L, reference.reportId());
        var saved = org.mockito.ArgumentCaptor.forClass(InspectionReportRevisionDO.class); verify(reports).updateById(saved.capture());
        assertEquals(80L, InspectionUmcReportResults.source(saved.getValue()).log().artifactId());
        assertNull(saved.getValue().getResultJson()); verifyNoInteractions(files, generated);
        assertEquals(90L, InspectionUmcReportResults.source(saved.getValue()).executionId());
    }

    @Test void completionPersistsResultAndStatusTogetherOnTheOriginalReport() {
        report.setStatusCode("GENERATING"); report.setResultJson(null); report.setResultSha256(null);
        when(files.selectForTask(any())).thenReturn(List.of()); when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        when(generated.create(any())).thenReturn(new NativeGeneratedFileApi.RegisteredFile(60L, 70L, 80L, 1, "a".repeat(64), "report.doc"));
        when(evidence.inspectDocument(7L, 70L)).thenReturn(new FileEvidenceApi.Document(70L, "SRV", "INSPECTION_TASK", "10",
                "UMC_REPORT", "doc-ref", 80L, 1, "a".repeat(64), "report.doc", true));
        byte[] doc = {1};
        var result = new UmcInspectionApi.Result(new UmcInspectionApi.Checkpoint("input", "75", "690", "ACCEPTED"), "report.doc",
                "application/vnd.ms-wordml", DigestUtil.sha256Hex(doc), doc, DigestUtil.sha256Hex(JSON), JSON,
                JsonUtils.parseObject(JSON, UmcInspectionApi.Report.class), List.of());
        var saved = transactions.complete(new InspectionUmcTransactions.Complete(request, result));
        assertEquals("AVAILABLE", saved.statusCode()); assertEquals("690", saved.detail().id());
        verify(reports, times(1)).updateById(argThat((InspectionReportRevisionDO row) -> row == report
                && row.getResultJson() != null && "AVAILABLE".equals(row.getStatusCode())));
        verify(files).insert(argThat((InspectionReportFileDO row) -> "DOC".equals(row.getFormatCode()) && row.getReportId().equals(20L)));
        assertNull(task.getCurrentReportId()); assertEquals("EXECUTING", task.getStatusCode());
    }

    @Test void resultLookupRejectsAnotherTenantOrTask() {
        report.setTenantId(8L);
        assertThrows(InspectionException.class, () -> transactions.detail(request));
        report.setTenantId(7L); report.setTaskId(11L);
        assertThrows(InspectionException.class, () -> transactions.detail(request));
        verifyNoInteractions(generated, content);
    }

    @Test void onlineCollectionUsesTheExactBoundExecutionAndNeverASubmittedOfflineFile() {
        var attempt=new InspectionAttemptDO();attempt.setId(30L);attempt.setTenantId(7L);attempt.setTaskId(10L);attempt.setMode("ONLINE");attempt.setProviderCode("UMC");
        var device=new InspectionTaskDeviceDO();device.setDeviceId(40L);device.setSerialNumber("TEST-SN");
        var log=new InspectionExecutionDO();log.setId(77L);log.setTenantId(7L);log.setTaskId(10L);log.setAttemptId(30L);log.setDeviceId(40L);
        log.setPurposeCode("COLLECT");log.setStatusCode("PENDING");log.setHost("192.0.2.10");log.setRecognizedSerial("TEST-SN");
        log.setArtifactId(50L);log.setCollectionResultVersion(1);log.setCollectionFileVersionId(500L);log.setCredentialSnapshotRecorded(true);log.setOriginalFileSha256("a".repeat(64));
        report.setSourceContextJson(JsonUtils.toJsonString(new InspectionUmcReportResults.Source(40L,"192.0.2.10","TEST-SN",true,null,new InspectionUmcReportResults.StoredLog(50L,2,"stored-input","a".repeat(64)),77L)));
        when(attempts.selectById(30L)).thenReturn(attempt);when(devices.selectForTask(any())).thenReturn(List.of(device));
        when(executions.selectById(77L)).thenReturn(log);when(executions.selectForTask(any())).thenReturn(List.of(log));when(reports.selectUmcSources(any())).thenReturn(List.of(report));
        var request=new InspectionUmcTransactions.ExecutionAccess(context,10L,77L);
        assertEquals(20L,transactions.prepareCollection(request).reportId());verifyNoInteractions(content,generated);
        log.setCredentialSnapshotRecorded(false);assertThrows(InspectionException.class,()->transactions.prepareCollection(request));log.setCredentialSnapshotRecorded(true);
        log.setTaskId(11L);assertThrows(InspectionException.class,()->transactions.prepareCollection(request));log.setTaskId(10L);
        log.setPurposeCode("PRECHECK");assertThrows(InspectionException.class,()->transactions.prepareCollection(request));log.setPurposeCode("COLLECT");
        log.setCollectionResultVersion(null);assertThrows(InspectionException.class,()->transactions.prepareCollection(request));
        attempt.setProviderCode("PLATFORM");assertThrows(InspectionException.class,()->transactions.prepareCollection(request));
    }
    private InspectionReportFileDO file(String format, byte[] bytes) {
        var row = new InspectionReportFileDO(); row.setTenantId(7L); row.setTaskId(10L); row.setReportId(20L);
        row.setFormatCode(format); row.setArtifactId(50L); row.setFileVersionNo(2); row.setReferenceKey("stored-" + format);
        row.setSha256(DigestUtil.sha256Hex(bytes)); return row;
    }
}
