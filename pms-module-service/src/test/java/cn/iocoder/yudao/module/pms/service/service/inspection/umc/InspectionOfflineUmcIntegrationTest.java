package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.service.controller.business.InspectionExecutionController;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionAttemptDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionAttemptMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InspectionOfflineUmcIntegrationTest {
    private final InspectionExecutionService executions = mock(InspectionExecutionService.class);
    private final InspectionCommandExecutor commands = mock(InspectionCommandExecutor.class);
    private final InspectionUmcService umc = mock(InspectionUmcService.class);
    private final InspectionUmcWorkflow workflow=mock(InspectionUmcWorkflow.class);
    private final InspectionExecutionController controller = new InspectionExecutionController(executions, commands, umc,workflow);
    private final InspectionRequestContext context = new InspectionRequestContext(7L, 9L, "offline-upload", "test", 3L);

    @Test void batchReceiptsKeepTheOriginalSummaryShapeAndReadHistoricalSummaryReceipts() {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(5L);
        var historical = JsonUtils.parseObject(JsonUtils.toJsonString(summary), InspectionExecutionService.BatchRegistration.class);
        assertEquals(10L, historical.getId()); assertEquals(5L, historical.getVersion()); assertNull(historical.sourceHosts());
        var receipt = new InspectionExecutionService.BatchRegistration(summary, java.util.List.of(new InspectionUmcService.Host(40L, "10.27.35.201")));
        var replay = JsonUtils.parseObject(JsonUtils.toJsonString(receipt), InspectionExecutionService.BatchRegistration.class);
        assertEquals(receipt.sourceHosts(), replay.sourceHosts()); assertEquals(5L, replay.summary().getVersion());
        assertFalse(JsonUtils.parseTree(JsonUtils.toJsonString(receipt.summary())).has("sourceHosts"));
    }

    @Test void umcSingleUploadDerivesSourceFromItsOriginalProtocolFilename() throws Exception {
        var controller = controllerWithProvider("UMC");
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(4L);
        when(commands.execute(any())).thenAnswer(call -> call.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.upload(any())).thenReturn(summary);
        // Capture is independent of source resolution and has its own tests.
        when(umc.fromOfflineLog(any())).thenThrow(new IllegalStateException("stop after source resolution"));
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            var failure = assertThrows(IllegalStateException.class, () -> controller.upload(10L, new HttpHeaders(), 30L, 40L, null,
                    null, new MockMultipartFile("file", "device(192.0.2.10)", "text/plain", new byte[]{1})));
            assertEquals("stop after source resolution", failure.getMessage());
            verify(umc).fromOfflineLog(argThat(c -> c.host().equals("192.0.2.10") && c.deviceId().equals(40L)));
            verify(executions).upload(argThat(c -> c.fileName().equals("device(192.0.2.10)")));
        }
    }

    @Test void platformUploadNeverInfersAnUmcSourceFromAProtocolLookingFilename() throws Exception {
        var controller = controllerWithProvider("PLATFORM");
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(4L);
        when(commands.execute(any())).thenAnswer(call -> call.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.upload(any())).thenReturn(summary);
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            assertSame(summary, controller.upload(10L, new HttpHeaders(), 30L, 40L, null,
                    null, new MockMultipartFile("file", "192.0.2.10", "text/plain", new byte[]{1})).getData());
            verifyNoInteractions(umc);
        }
    }

    @Test void unidentifiableUmcFilenameStillRequiresSourceBeforeAnyStorageOrSubmission() {
        var controller = controllerWithProvider("UMC");
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            var failure = assertThrows(InspectionException.class, () -> controller.upload(10L, new HttpHeaders(), 30L, 40L, null,
                    null, new MockMultipartFile("file", "capture.log", "text/plain", "address 192.0.2.10".getBytes(StandardCharsets.UTF_8))));
            assertTrue(failure.getMessage().contains("IP"));
            verifyNoInteractions(commands, executions, umc);
        }
    }

    private InspectionExecutionController controllerWithProvider(String provider) {
        var attempts = mock(InspectionAttemptMapper.class);
        var attempt = new InspectionAttemptDO(); attempt.setId(30L); attempt.setTenantId(7L); attempt.setTaskId(10L); attempt.setProviderCode(provider);
        when(attempts.selectById(30L)).thenReturn(attempt);
        var workflow = new InspectionUmcWorkflow(null, null, attempts, null, null, null, null, null, null, null);
        return new InspectionExecutionController(executions, commands, umc, workflow);
    }

    @Test void originalSingleUploadCallsUmcAfterItsExistingTransactionWithNewTaskVersion() throws Exception {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(4L);
        var completed=new InspectionViews.TaskSummary();completed.setId(10L);completed.setVersion(5L);
        when(commands.execute(any())).thenAnswer(invocation -> invocation.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.upload(any())).thenReturn(summary);
        when(workflow.captureUploaded(any())).thenReturn(completed);
        byte[] bytes = "synthetic log".getBytes(StandardCharsets.UTF_8); String digest = DigestUtil.sha256Hex(bytes);
        var file = new MockMultipartFile("file", "test.log", "text/plain", bytes);
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            var response = controller.upload(10L, new HttpHeaders(), 30L, 40L, null, "192.0.2.10", file);
            assertSame(completed, response.getData());
            var order = inOrder(executions, umc); order.verify(executions).upload(any());
            order.verify(umc).fromOfflineLog(argThat(c -> c.context().requireVersion() == 4L && c.deviceId().equals(40L)
                    && c.taskId().equals(10L) && c.host().equals("192.0.2.10") && c.digest().equals(digest)));
            verifyNoMoreInteractions(umc);
            verify(workflow).captureUploaded(argThat(c->c.context().requireVersion()==4L && c.digest().equals(digest)));
        }
    }

    @Test void originalBatchUploadPassesDeviceIpBindingsToExistingRegistrationThenUmc() throws Exception {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(5L);
        when(commands.execute(any())).thenAnswer(invocation -> invocation.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.uploadBatch(any())).thenReturn(summary);
        when(workflow.captureUploaded(any())).thenReturn(summary);
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            controller.uploadBatch(10L, new HttpHeaders(), 30L, "[{\"deviceId\":\"40\",\"host\":\"192.0.2.10\"}]",
                    new MockMultipartFile("file", "batch.log", "text/plain", new byte[]{1}));
            verify(executions).uploadBatch(argThat(c -> c.sourceHosts().size() == 1 && c.sourceHosts().getFirst().deviceId().equals(40L)));
            verify(umc).fromOfflineBatch(argThat(c -> c.context().requireVersion() == 5L && c.hosts().getFirst().host().equals("192.0.2.10")));
        }
    }

    @Test void multiFilesAreRegisteredTogetherAndCaptureUsesTheirOrderedDigest() throws Exception {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(5L);
        when(commands.execute(any())).thenAnswer(c -> c.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.uploadFiles(any())).thenReturn(new InspectionExecutionService.BatchRegistration(summary,
                java.util.List.of(new InspectionUmcService.Host(40L, "192.0.2.10"))));
        when(workflow.captureUploaded(any())).thenReturn(summary);
        var files = java.util.List.of(new MockMultipartFile("files", "a.log", "text/plain", new byte[]{1}),
                new MockMultipartFile("files", "b.txt", "text/plain", new byte[]{2}));
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            controller.uploadFiles(10L, new HttpHeaders(), 30L, "[{\"deviceId\":\"40\",\"host\":\"192.0.2.10\"}]", null, java.util.List.copyOf(files));
            verify(executions).uploadFiles(argThat(c -> c.files().size() == 2 && c.files().getFirst().fileName().equals("a.log")));
            verify(umc).fromOfflineBatch(argThat(c -> c.digest().equals(InspectionExecutionService.batchDigest(java.util.List.of(
                    new InspectionExecutionService.UploadFile("a.log", new byte[]{1}), new InspectionExecutionService.UploadFile("b.txt", new byte[]{2}))))));
            verify(workflow).captureUploaded(argThat(c -> c.context().requireVersion() == 5L));
        }
    }

    @Test void multipleFilesResolveIpInsideTheRegistrationBeforeUmcAndReplayRetainsResolvedSources() throws Exception {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(5L);
        var sources = java.util.List.of(new InspectionUmcService.Host(40L, "10.27.35.201"), new InspectionUmcService.Host(41L, "10.27.35.202"));
        var receipt = new InspectionExecutionService.BatchRegistration(summary, sources);
        when(commands.execute(any())).thenAnswer(c -> c.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.uploadFiles(any())).thenReturn(receipt);
        when(workflow.captureUploaded(any())).thenReturn(summary);
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            var files = java.util.List.of(new MockMultipartFile("files", "10.27.35.201.txt", "text/plain", new byte[]{1}),
                    new MockMultipartFile("files", "10.27.35.202.txt", "text/plain", new byte[]{2}));
            controller.uploadFiles(10L, new HttpHeaders(), 30L, null, null, java.util.List.copyOf(files));
            verify(executions).uploadFiles(argThat(c -> c.sourceHosts() == null));
            verify(umc).fromOfflineBatch(argThat(c -> c.hosts().equals(sources)));
            reset(executions, umc);
            doAnswer(c -> {
                var write = c.<InspectionCommandExecutor.Write<?>>getArgument(0);
                return write.responseType() == InspectionExecutionService.BatchRegistration.class ? receipt : write.action().get();
            }).when(commands).execute(any());
            controller.uploadFiles(10L, new HttpHeaders(), 30L, null, null, java.util.List.copyOf(files));
            verifyNoInteractions(executions);
            verify(umc).fromOfflineBatch(argThat(c -> c.hosts().equals(sources)));
        }
    }
    @Test void ambiguousOrEmptyMultiFileInputCannotRegisterOrSubmitAnything() {
        var f = new MockMultipartFile("files", "a.log", "text/plain", new byte[]{1});
        assertThrows(InspectionException.class, () -> controller.uploadFiles(10L, new HttpHeaders(), 30L, null, f, java.util.List.of(f)));
        assertThrows(InspectionException.class, () -> controller.uploadFiles(10L, new HttpHeaders(), 30L, null, null, java.util.List.of()));
        assertThrows(InspectionException.class, () -> controller.uploadFiles(10L, new HttpHeaders(), 30L, null, null,
                java.util.List.of(new MockMultipartFile("files", "empty.log", "text/plain", new byte[0]))));
        verifyNoInteractions(executions, umc, commands);
    }

    @Test void invalidSourceIpIsRejectedBeforeRegisteringOrSubmittingLogs() {
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            assertThrows(InspectionException.class, () -> controller.upload(10L, new HttpHeaders(), 30L, 40L, null,
                    "192.0.2.999", new MockMultipartFile("file", "test.log", "text/plain", new byte[]{1})));
            verifyNoInteractions(commands, executions, umc);
        }
    }

    @Test void explicitSourceKeepsPrecedenceOverAnArbitraryOriginalFilename() throws Exception {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(4L);
        when(commands.execute(any())).thenAnswer(call -> call.<InspectionCommandExecutor.Write<?>>getArgument(0).action().get());
        when(executions.upload(any())).thenReturn(summary);
        when(workflow.captureUploaded(any())).thenReturn(summary);
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            controller.upload(10L, new HttpHeaders(), 30L, 40L, null, "192.0.2.11",
                    new MockMultipartFile("file", "192.0.2.10", "text/plain", new byte[]{1}));
            verify(umc).fromOfflineLog(argThat(c -> c.host().equals("192.0.2.11")));
            verify(workflow, never()).inferUploadSource(any());
        }
    }

    @Test void existingRefreshActionObtainsUmcExecutionResultsWithoutStartingTheUnrelatedParser() {
        var summary = new InspectionViews.TaskSummary(); summary.setId(10L); summary.setVersion(3L);
        when(umc.refreshOfflineExecution(any())).thenReturn(summary);
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(() -> InspectionRequestContext.write(any())).thenReturn(context);
            assertSame(summary, controller.refresh(10L, new HttpHeaders(), new InspectionExecutionController.Refresh(90L)).getData());
            verify(umc).refreshOfflineExecution(argThat(r -> r.taskId().equals(10L) && r.executionId().equals(90L)));
            verifyNoInteractions(commands, executions);
        }
    }
}
