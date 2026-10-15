package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.platform.api.file.AuthenticatedFileContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFileCommand;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionAttemptRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionExecutionRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionParserDocuments;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService.CommandDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionExecutionServiceTest {
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final InspectionTaskService tasks = mock(InspectionTaskService.class);
    private final InspectionAttemptMapper attempts = mock(InspectionAttemptMapper.class);
    private final InspectionTaskDeviceMapper devices = mock(InspectionTaskDeviceMapper.class);
    private final InspectionTaskRuleSnapshotMapper snapshots = mock(InspectionTaskRuleSnapshotMapper.class);
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionParsePartMapper parts = mock(InspectionParsePartMapper.class);
    private final InspectionDetectionResultMapper results = mock(InspectionDetectionResultMapper.class);
    private final InspectionRuleQueryService rules = mock(InspectionRuleQueryService.class);
    private final DeviceLogParsingApi parser = mock(DeviceLogParsingApi.class);
    private final NativeGeneratedFileApi generatedFiles = mock(NativeGeneratedFileApi.class);
    private final FileEvidenceApi fileEvidence = mock(FileEvidenceApi.class);
    private final AuthenticatedFileContentApi fileContent = mock(AuthenticatedFileContentApi.class);
    private final InspectionRequestContext context = new InspectionRequestContext(1L, 2L, "refresh", "test", 5L);
    private final Map<Long, InspectionExecutionDO> executionRows = new HashMap<>();
    private final Map<Long, List<InspectionParsePartDO>> jobRows = new HashMap<>();
    private final Map<Long, byte[]> documents = new HashMap<>();
    private final List<InspectionDetectionResultDO> appendedResults = new ArrayList<>();
    private InspectionExecutionService service;
    private InspectionTaskDO task;
    private long nextArtifact = 900L;

    @BeforeEach
    void setup() {
        service = new InspectionExecutionService(access, tasks, attempts, devices, snapshots, executions,
                parts, results, rules, parser, generatedFiles, fileEvidence, fileContent);
        task = new InspectionTaskDO();
        task.setId(10L); task.setTenantId(1L); task.setVersion(5L); task.setProjectId(100L);
        task.setCurrentAttemptId(20L); task.setStatusCode("EXECUTING");
        var device = new InspectionTaskDeviceDO();
        device.setId(60L); device.setTenantId(1L); device.setTaskId(10L);
        device.setDeviceId(30L); device.setSerialNumber("SELECTED-SN");
        var attempt = new InspectionAttemptDO();
        attempt.setId(20L); attempt.setTenantId(1L); attempt.setTaskId(10L);
        attempt.setMode("ONLINE"); attempt.setStatusCode("ACTIVE");
        when(access.task(any(InspectionAccessService.TaskAccess.class))).thenReturn(task);
        when(devices.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of(device));
        when(attempts.selectById(20L)).thenReturn(attempt);
        when(executions.selectById(anyLong())).thenAnswer(call -> executionRows.get(call.getArgument(0)));
        when(parts.selectForExecution(any(InspectionExecutionRows.class))).thenAnswer(call -> {
            InspectionExecutionRows query = call.getArgument(0);
            return jobRows.getOrDefault(query.executionId(), List.of());
        });
        when(fileContent.read(any(AuthenticatedFileContentApi.ReadCommand.class))).thenAnswer(call -> {
            AuthenticatedFileContentApi.ReadCommand command = call.getArgument(0);
            return documents.get(command.file().artifactId());
        });
        when(results.selectCurrent(any(InspectionAttemptRows.class))).thenAnswer(call -> {
            Map<Long, InspectionDetectionResultDO> latest = new LinkedHashMap<>();
            appendedResults.forEach(row -> latest.put(row.getRuleSnapshotId(), row));
            return List.copyOf(latest.values());
        });
        when(results.insert(any(InspectionDetectionResultDO.class))).thenAnswer(call -> {
            InspectionDetectionResultDO row = call.getArgument(0);
            row.setId(1000L + appendedResults.size()); appendedResults.add(row); return 1;
        });
        when(generatedFiles.create(any(NativeGeneratedFileCommand.class))).thenAnswer(call -> {
            NativeGeneratedFileCommand command = call.getArgument(0);
            long artifact = nextArtifact++;
            documents.put(artifact, command.content());
            return new NativeGeneratedFileApi.RegisteredFile(artifact, artifact, artifact, 1, "digest", command.fileName());
        });
        when(fileEvidence.inspectDocument(eq(1L), anyLong())).thenAnswer(call -> {
            Long artifact = call.getArgument(1);
            return new FileEvidenceApi.Document(artifact, "SRV", "INSPECTION_TASK", "10", "LOG",
                    "log-" + artifact, artifact, 1, "digest", "normalized.json", true);
        });
        when(parts.insert(any(InspectionParsePartDO.class))).thenAnswer(call -> {
            InspectionParsePartDO row = call.getArgument(0);
            row.setId(100L + row.getPartNo());
            jobRows.computeIfAbsent(row.getExecutionId(), id -> new ArrayList<>()).add(row); return 1;
        });
    }

    @Test
    void failedCollectionCannotPassEvenWhenItsPreservedOutputMeetsTheRule() {
        freeze(rule(201L, "OK", command("latency", "show latency", 1)));
        var execution = execution(50L, "COLLECT");
        execution.setCollectionStatus("FAILED");
        var block = block(1, "show latency", "OK");
        when(parser.normalize(any())).thenReturn(normalized(List.of(block), List.of("SELECTED-SN")));

        service.ingestCollection(new InspectionExecutionService.Ingest(context, 10L, 50L, collectionLog(List.of(block), false)));
        var job = jobRows.get(50L).getFirst();
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job, List.of(unit(block, "10ms"))));
        refresh(execution);

        var result = appendedResults.getFirst();
        assertEquals("ERROR", result.getOutcome());
        assertEquals("COLLECTION_NOT_SUCCEEDED", result.getDetailCode());
        assertEquals("OK", result.getOutputSummary());
        assertNull(result.getMeasuredValue());
        assertEquals("FAILED", execution.getCollectionStatus());
        assertEquals(1, appendedResults.size());
    }

    @Test
    void duplicateAndMultilineCommandsKeepTheirFrozenSequenceAndIndependentOutputs() {
        var first = rule(201L, "first\\nsecond\\nthird",
                command("repeat", "show duplicate", 2), command("multiline", "show first\r\nshow duplicate", 1));
        var last = rule(202L, "final", command("final", "show final", 1));
        // Deliberately return snapshots and commands out of order; their frozen order owns the collection indexes.
        freeze(first, last);
        when(snapshots.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of(snapshot(102L, 202L), snapshot(101L, 201L)));
        var frozen = service.commands(new InspectionExecutionService.CommandList(context, 10L, 30L));
        assertEquals(List.of("show first", "show duplicate", "show duplicate", "show final"),
                frozen.stream().map(InspectionExecutionService.CommandView::content).toList());
        assertEquals(List.of(1, 2, 3, 4), frozen.stream().map(InspectionExecutionService.CommandView::sequenceNo).toList());
        assertEquals(List.of(201L, 201L, 201L, 202L), frozen.stream().map(InspectionExecutionService.CommandView::ruleRevisionId).toList());
        var blocks = List.of(block(1, "show first", "first"), block(2, "show duplicate", "second"),
                block(3, "show duplicate", "third"), block(4, "show final", "final"));
        var execution = execution(50L, "COLLECT");
        var job = part(execution, 1, 4);
        register(new FrozenLog(execution, blocks, List.of(job)));
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job,
                List.of(unit(blocks.get(0), "10ms"), unit(blocks.get(1), "10ms"), unit(blocks.get(2), "10ms"), unit(blocks.get(3), "15ms"))));

        refresh(execution);

        assertEquals(2, appendedResults.size());
        assertEquals("PASS", appendedResults.get(0).getOutcome());
        assertEquals("first\nsecond\nthird", appendedResults.get(0).getOutputSummary());
        assertEquals("ABNORMAL", appendedResults.get(1).getOutcome());
        assertEquals("THRESHOLD_NOT_MET", appendedResults.get(1).getDetailCode());
        assertEquals(202L, appendedResults.get(1).getRuleRevisionId());
        assertEquals(0, new BigDecimal("15").compareTo(appendedResults.get(1).getMeasuredValue()));
    }

    @Test
    void collectedOutputAtAnotherCommandIndexCannotSatisfyACommandWithTheSameText() {
        freeze(rule(201L, "OK", command("a", "show first", 1)), rule(202L, "OK", command("b", "show second", 1)));
        var blocks = List.of(block(1, "show second", "OK"), block(2, "show first", "OK"));
        var execution = execution(50L, "COLLECT");
        var job = part(execution, 1, 2);
        register(new FrozenLog(execution, blocks, List.of(job)));
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job,
                blocks.stream().map(block -> unit(block, "10ms")).toList()));

        refresh(execution);

        assertEquals(2, appendedResults.size());
        for (var result : appendedResults) {
            assertEquals("UNJUDGED", result.getOutcome());
            assertEquals("COMMAND_MISSING", result.getDetailCode());
        }
    }

    @Test
    void parsedValuesRequireBothTheSourceCommandIndexAndTextBeforeTheyCanSatisfyThresholds() {
        freeze(rule(201L, "OK", command("a", "show first", 1)), rule(202L, "OK", command("b", "show second", 1)));
        var blocks = List.of(block(1, "show first", "OK"), block(2, "show second", "OK"));
        var execution = execution(50L, "COLLECT");
        var job = part(execution, 1, 2);
        register(new FrozenLog(execution, blocks, List.of(job)));
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job, List.of(
                unit(block(3, "show first", "OK"), "10ms"),
                unit(block(2, "show unrelated", "OK"), "10ms"))));

        refresh(execution);

        assertEquals(2, appendedResults.size());
        for (var result : appendedResults) {
            assertEquals("UNJUDGED", result.getOutcome());
            assertEquals("STRUCTURE_LIMITED", result.getDetailCode());
            assertNull(result.getMeasuredValue());
        }
    }

    @Test
    void offlineSupplementCompletesMissingRuleWithoutOverwritingOtherRulesOrTheirHistory() {
        freeze(rule(201L, "OK", command("a", "show first", 1)), rule(202L, "OK", command("b", "show second", 1)));
        parseOffline(50L, block(1, "show first", "OK"));
        assertEquals(2, appendedResults.size());
        var firstPass = appendedResults.get(0);
        var missing = appendedResults.get(1);
        assertEquals("PASS", firstPass.getOutcome());
        assertEquals("UNJUDGED", missing.getOutcome());
        assertEquals("COMMAND_MISSING", missing.getDetailCode());

        parseOffline(51L, block(1, "show second", "OK"));

        assertEquals(3, appendedResults.size());
        assertSame(firstPass, appendedResults.get(0));
        assertEquals("PASS", firstPass.getOutcome());
        assertEquals("UNJUDGED", missing.getOutcome());
        var supplemented = appendedResults.get(2);
        assertEquals(102L, supplemented.getRuleSnapshotId());
        assertEquals(51L, supplemented.getExecutionId());
        assertEquals("PASS", supplemented.getOutcome());
        verify(results, never()).updateById(any(InspectionDetectionResultDO.class));

        parseOffline(52L, block(1, "show unrelated", "OK"));
        assertEquals(3, appendedResults.size(), "An unrelated log cannot replace either current rule result");
    }

    @Test
    void collectionSerialMismatchStoresEvidenceButNeverCreatesParsingOrDetectionResults() {
        var execution = execution(50L, "COLLECT");
        var block = block(1, "show latency", "OK");
        when(parser.normalize(any())).thenReturn(normalized(List.of(block), List.of("OTHER-SN")));

        service.ingestCollection(new InspectionExecutionService.Ingest(context, 10L, 50L, collectionLog(List.of(block), false)));
        refresh(execution);

        assertEquals("FAILED", execution.getStatusCode());
        assertEquals("DEVICE_SERIAL_MISMATCH", execution.getFailureCode());
        assertNotNull(execution.getArtifactId());
        verify(parts, never()).insert(any(InspectionParsePartDO.class));
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
        verify(parser, never()).find(anyString(), anyString(), any());
        verify(fileContent, never()).read(any());
    }

    @Test
    void serialMismatchInSemanticResultCannotProduceAPassingDetection() {
        freeze(rule(201L, "OK", command("latency", "show latency", 1)));
        var block = block(1, "show latency", "OK");
        var execution = execution(50L, "COLLECT");
        var job = part(execution, 1, 1);
        register(new FrozenLog(execution, List.of(block), List.of(job)));
        Map<String, Object> semantic = Map.of("genericContent", Map.of("units", List.of(unit(block, "10ms"))),
                "snapshot", List.of(Map.of("semanticKey", "device.identity.serialNumber", "status", "OBSERVED", "value", "OTHER-SN")));
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(new DeviceLogParsingApi.ParseTask(
                "parser-" + job.getRequestId(), "SUCCEEDED", null, "release-1", List.of(), Map.of("semanticResult", semantic)));

        refresh(execution);

        assertEquals("FAILED", execution.getStatusCode());
        assertEquals("DEVICE_SERIAL_MISMATCH", execution.getFailureCode());
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
    }

    @Test
    void repeatedRefreshReadsSuccessfulPartWithoutUpdatingItsCompletionTimeWhileOtherPartFinishes() {
        freeze(rule(201L, "first\\nsecond", command("both", "show first\nshow second", 1)));
        var execution = execution(50L, "COLLECT");
        var firstBlock = block(1, "show first", "first");
        var secondBlock = block(2, "show second", "second");
        var first = completedPart(execution);
        var second = part(execution, 2, 2);
        var originalCompletion = first.getCompletedAt();
        register(new FrozenLog(execution, List.of(firstBlock, secondBlock), List.of(first, second)));
        when(parser.find(first.getRequestId(), "100", null)).thenReturn(succeeded(first, List.of(unit(firstBlock, "10ms"))));
        when(parser.find(second.getRequestId(), "100", null)).thenReturn(
                new DeviceLogParsingApi.ParseTask("parser-" + second.getRequestId(), "RUNNING", null, "release-1", List.of(), null),
                succeeded(second, List.of(unit(secondBlock, "10ms"))));

        refresh(execution);
        assertEquals("RUNNING", execution.getStatusCode());
        assertTrue(appendedResults.isEmpty());
        assertEquals(originalCompletion, first.getCompletedAt());
        refresh(execution);

        assertEquals("COMPLETE", execution.getStatusCode());
        assertEquals("PASS", appendedResults.getFirst().getOutcome());
        assertEquals("first\nsecond", appendedResults.getFirst().getOutputSummary());
        assertEquals(originalCompletion, first.getCompletedAt());
        verify(parts, never()).updateById(same(first));
        verify(parts, times(2)).updateById(same(second));
        verify(parser, times(2)).find(first.getRequestId(), "100", null);
    }

    @Test
    void temporarilyUnavailableSuccessfulPartRemainsSuccessfulAndWaitsForItsOriginalEvidence() {
        var execution = execution(50L, "OFFLINE");
        var first = completedPart(execution);
        var second = part(execution, 2, 2);
        var originalCompletion = first.getCompletedAt();
        register(new FrozenLog(execution, List.of(block(1, "show first", "OK"), block(2, "show second", "OK")), List.of(first, second)));
        when(parser.find(first.getRequestId(), "100", null)).thenReturn(null);
        when(parser.find(second.getRequestId(), "100", null)).thenReturn(new DeviceLogParsingApi.ParseTask(
                "parser-" + second.getRequestId(), "RUNNING", null, "release-1", List.of(), null));

        refresh(execution);

        assertEquals("RUNNING", execution.getStatusCode());
        assertEquals("SUCCEEDED", first.getStatusCode());
        assertEquals(originalCompletion, first.getCompletedAt());
        assertNull(first.getFailureCode());
        verify(parts, never()).updateById(same(first));
        verify(parser, never()).submit(any());
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
    }

    @Test
    void successfulPartRejectsAChangedRemoteStateOrReleaseWithoutRewritingHistory() {
        var execution = execution(50L, "OFFLINE");
        var job = completedPart(execution);
        var originalCompletion = job.getCompletedAt();
        register(new FrozenLog(execution, List.of(block(1, "show first", "OK")), List.of(job)));
        for (var remote : List.of(
                new DeviceLogParsingApi.ParseTask(job.getParserTaskId(), "FAILED", null, "release-1", List.of(), null),
                new DeviceLogParsingApi.ParseTask(job.getParserTaskId(), "SUCCEEDED", null, "release-2", List.of(), Map.of()))) {
            when(parser.find(job.getRequestId(), "100", null)).thenReturn(remote);
            assertThrows(InspectionException.class, () -> refresh(execution));
            assertEquals("SUCCEEDED", job.getStatusCode());
            assertEquals("release-1", job.getParserRelease());
            assertEquals(originalCompletion, job.getCompletedAt());
        }
        verify(parts, never()).updateById(any(InspectionParsePartDO.class));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
    }

    @Test
    void invalidOfflineLogIsAFileErrorAndStoresNoOriginalOrExecution() {
        attempts.selectById(20L).setMode("OFFLINE");
        when(parser.normalize(any())).thenThrow(new DeviceLogParsingApi.InvalidInputException("日志格式无效"));

        var failure = assertThrows(InspectionException.class, () -> service.upload(
                new InspectionExecutionService.Upload(context, 10L, 20L, 30L, "invalid.log", bytes("invalid log"), null)));

        assertEquals(InspectionException.Reason.FILE_INVALID, failure.getReason());
        verifyNoInteractions(generatedFiles);
        verify(executions, never()).insert(any(InspectionExecutionDO.class));
        verify(parts, never()).insert(any(InspectionParsePartDO.class));
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
        verify(tasks, never()).save(any());
    }

    @Test
    void offlineNormalizerOutageRemainsDependencyFailureWithoutStoringContent() {
        attempts.selectById(20L).setMode("OFFLINE");
        when(parser.normalize(any())).thenThrow(new DeviceLogParsingApi.ParsingException("不可用"));

        var failure = assertThrows(InspectionException.class, () -> service.upload(
                new InspectionExecutionService.Upload(context, 10L, 20L, 30L, "valid.log", bytes("valid log"), null)));

        assertEquals(InspectionException.Reason.DEPENDENCY_UNAVAILABLE, failure.getReason());
        verifyNoInteractions(generatedFiles);
        verify(executions, never()).insert(any(InspectionExecutionDO.class));
        verify(tasks, never()).save(any());
    }

    @Test
    void rejectedCollectionInputRetainsItsSourceAndDoesNotClaimAnOutage() {
        var execution = execution(50L, "COLLECT");
        when(parser.normalize(any())).thenThrow(new DeviceLogParsingApi.InvalidInputException("日志格式无效"));

        var failure = assertThrows(InspectionException.class, () -> service.ingestCollection(
                new InspectionExecutionService.Ingest(context, 10L, 50L,
                        collectionLog(List.of(block(1, "show latency", "OK")), false))));

        assertEquals(InspectionException.Reason.FILE_INVALID, failure.getReason());
        assertEquals("collection-50", execution.getCollectionTaskId());
        assertEquals(80L, execution.getCollectionFileVersionId());
        assertNull(execution.getArtifactId());
        verify(parser).normalize(any(DeviceLogParsingApi.LogInput.class));
        verifyNoInteractions(generatedFiles);
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        verify(parts, never()).insert(any(InspectionParsePartDO.class));
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
        verify(tasks, never()).save(any());
    }

    @Test
    void collectionEnvelopeProjectsOnlyCanonicalFieldsBeforeNormalizationAndRetainsItsSource() {
        var execution = execution(50L, "COLLECT");
        var first = block(2, "show first", "合成原始输出\n10ms");
        var last = new InspectionParserDocuments.Block(8, "show last", "FAILED", "保留失败输出\n",
                "合成错误信息\n", true, 17);
        byte[] original = collectionLog(List.of(last, first), false);
        when(parser.normalize(any())).thenAnswer(call -> canonicalNormalization(call.getArgument(0)));

        service.ingestCollection(new InspectionExecutionService.Ingest(context, 10L, 50L, original));

        var input = ArgumentCaptor.forClass(DeviceLogParsingApi.LogInput.class);
        verify(parser).normalize(input.capture());
        assertEquals("100", input.getValue().projectKey());
        var canonical = JsonUtils.parseTree(input.getValue().inputText());
        assertEquals(Set.of("schemaVersion", "commandBlocks"), Set.copyOf(canonical.propertyNames()));
        assertEquals("1.0.0", canonical.path("schemaVersion").asText());
        for (var block : canonical.path("commandBlocks")) {
            assertEquals(Set.of("commandIndex", "commandText", "status", "stdout", "stderr", "truncated", "exitCode"),
                    Set.copyOf(block.propertyNames()));
        }
        assertEquals(List.of(first, last), InspectionParserDocuments.decode(bytes(input.getValue().inputText())).commandBlocks());
        assertEquals(List.of(first, last), InspectionParserDocuments.decode(documents.get(execution.getArtifactId())).commandBlocks());
        assertEquals(DigestUtil.sha256Hex(original), execution.getOriginalFileSha256());
        assertEquals("collection-50", execution.getCollectionTaskId());
        assertEquals(80L, execution.getCollectionFileVersionId());
    }

    @ParameterizedTest(name = "root truncated={0}, block truncated={1}, status={2}")
    @CsvSource({"true, false, SUCCEEDED, UNJUDGED, STRUCTURE_LIMITED",
            "false, true, SUCCEEDED, UNJUDGED, STRUCTURE_LIMITED",
            "false, false, FAILED, ERROR, COMMAND_FAILED"})
    void truncatedOrFailedCollectionBlocksCannotPassMatchingTextAndThresholds(boolean rootTruncated,
            boolean blockTruncated, String status, String outcome, String detailCode) {
        freeze(rule(201L, "OK", command("latency", "show latency", 1)));
        var execution = execution(50L, "COLLECT");
        var first = new InspectionParserDocuments.Block(1, "show latency", status, "OK", "",
                blockTruncated, "FAILED".equals(status) ? 17 : 0);
        when(parser.normalize(any())).thenAnswer(call -> canonicalNormalization(call.getArgument(0)));

        service.ingestCollection(new InspectionExecutionService.Ingest(context, 10L, 50L,
                collectionLog(List.of(first, block(2, "show extra", "extra")), rootTruncated)));
        var stored = InspectionParserDocuments.decode(documents.get(execution.getArtifactId())).commandBlocks();
        assertEquals(rootTruncated || blockTruncated, stored.getFirst().truncated());
        if (rootTruncated) assertTrue(stored.stream().allMatch(block -> Boolean.TRUE.equals(block.truncated())));
        var job = jobRows.get(50L).getFirst();
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job,
                stored.stream().map(block -> unit(block, "10ms")).toList()));
        refresh(execution);

        assertEquals(1, appendedResults.size());
        assertEquals(outcome, appendedResults.getFirst().getOutcome());
        assertEquals(detailCode, appendedResults.getFirst().getDetailCode());
        assertEquals("OK", appendedResults.getFirst().getOutputSummary());
        assertNull(appendedResults.getFirst().getMeasuredValue());
    }

    @Test
    void collectionIndexGapsAndOrderCannotRenumberOutputsIntoPassingOtherFrozenCommands() {
        freeze(rule(201L, "OK", command("first", "show first", 1)),
                rule(202L, "OK", command("second", "show second", 1)));
        var execution = execution(50L, "COLLECT");
        var first = block(2, "show first", "OK");
        var last = block(7, "show second", "OK");
        when(parser.normalize(any())).thenAnswer(call -> canonicalNormalization(call.getArgument(0)));

        service.ingestCollection(new InspectionExecutionService.Ingest(context, 10L, 50L,
                collectionLog(List.of(last, first), false)));
        var stored = InspectionParserDocuments.decode(documents.get(execution.getArtifactId())).commandBlocks();
        assertEquals(List.of(first, last), stored);
        var job = jobRows.get(50L).getFirst();
        assertEquals(2, job.getFirstCommandIndex());
        assertEquals(7, job.getLastCommandIndex());
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job,
                stored.stream().map(block -> unit(block, "10ms")).toList()));
        refresh(execution);

        assertEquals(2, appendedResults.size());
        for (var result : appendedResults) {
            assertEquals("UNJUDGED", result.getOutcome());
            assertEquals("COMMAND_MISSING", result.getDetailCode());
            assertNull(result.getMeasuredValue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_BLOCKS", "EMPTY_BLOCKS", "NULL_BLOCK", "MISSING_ROOT_TRUNCATED",
            "MISSING_BLOCK_TRUNCATED", "MISSING_INDEX", "MISSING_TEXT", "MISSING_STATUS",
            "DUPLICATE_INDEX", "ZERO_INDEX", "NEGATIVE_INDEX"})
    void invalidCollectionBlocksAreRejectedBeforeNormalizationOrAnySrvPersistence(String defect) {
        var execution = execution(50L, "COLLECT");
        when(parser.normalize(any())).thenReturn(normalized(List.of(block(1, "show latency", "OK")), List.of("SELECTED-SN")));

        var failure = assertThrows(InspectionException.class, () -> service.ingestCollection(
                new InspectionExecutionService.Ingest(context, 10L, 50L, invalidCollectionLog(defect))));

        assertEquals(InspectionException.Reason.FILE_INVALID, failure.getReason());
        assertEquals("collection-50", execution.getCollectionTaskId());
        assertEquals(80L, execution.getCollectionFileVersionId());
        assertNull(execution.getArtifactId());
        assertNull(execution.getOriginalFileSha256());
        verifyNoInteractions(parser, generatedFiles);
        verify(executions, never()).insert(any(InspectionExecutionDO.class));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        verify(parts, never()).insert(any(InspectionParsePartDO.class));
        verify(results, never()).insert(any(InspectionDetectionResultDO.class));
        verify(tasks, never()).save(any());
    }

    private void refresh(InspectionExecutionDO execution) {
        service.refresh(new InspectionExecutionService.Refresh(context, task.getId(), execution.getId()));
    }

    private void parseOffline(long id, InspectionParserDocuments.Block block) {
        var execution = execution(id, "OFFLINE");
        var job = part(execution, block.commandIndex(), block.commandIndex());
        register(new FrozenLog(execution, List.of(block), List.of(job)));
        when(parser.find(job.getRequestId(), "100", null)).thenReturn(succeeded(job, List.of(unit(block, "10ms"))));
        refresh(execution);
    }

    private InspectionExecutionDO execution(long id, String purpose) {
        var row = new InspectionExecutionDO();
        row.setId(id); row.setTenantId(1L); row.setVersion(1L); row.setTaskId(10L);
        row.setAttemptId(20L); row.setDeviceId(30L); row.setPurposeCode(purpose); row.setStatusCode("PENDING");
        row.setCollectionStatus("SUCCEEDED"); row.setCollectionResultVersion(1);
        row.setCollectionFileVersionId(80L); row.setCollectionTaskId("collection-" + id);
        executionRows.put(id, row); return row;
    }

    private record FrozenLog(InspectionExecutionDO execution, List<InspectionParserDocuments.Block> blocks,
                             List<InspectionParsePartDO> jobs) { }

    private void register(FrozenLog log) {
        long artifact = nextArtifact++;
        log.execution().setArtifactId(artifact); log.execution().setFileVersionNo(1);
        log.execution().setFileReferenceKey("log-" + artifact);
        documents.put(artifact, InspectionParserDocuments.encode(log.blocks()));
        jobRows.put(log.execution().getId(), log.jobs());
    }

    private InspectionParsePartDO part(InspectionExecutionDO execution, int first, int last) {
        var row = new InspectionParsePartDO();
        row.setId(execution.getId() * 10 + first); row.setTenantId(1L); row.setVersion(1L);
        row.setTaskId(10L); row.setExecutionId(execution.getId()); row.setPartNo(first);
        row.setFirstCommandIndex(first); row.setLastCommandIndex(last);
        row.setRequestId("request-" + execution.getId() + "-" + first); row.setStatusCode("PENDING");
        return row;
    }

    private InspectionParsePartDO completedPart(InspectionExecutionDO execution) {
        var row = part(execution, 1, 1);
        row.setStatusCode("SUCCEEDED"); row.setParserTaskId("parser-" + row.getRequestId());
        row.setParserRelease("release-1"); row.setCompletedAt(LocalDateTime.of(2026, 10, 15, 8, 0));
        return row;
    }

    private void freeze(InspectionRuleQueryService.RuleView... views) {
        List<InspectionTaskRuleSnapshotDO> rows = new ArrayList<>();
        for (var view : views) {
            rows.add(snapshot(101L + rows.size(), view.getRevisionId()));
            when(rules.detail(view.getRevisionId())).thenReturn(view);
        }
        when(snapshots.selectForTask(any(InspectionTaskRows.class))).thenReturn(rows);
    }

    private InspectionTaskRuleSnapshotDO snapshot(long id, long revisionId) {
        var row = new InspectionTaskRuleSnapshotDO();
        row.setId(id); row.setTenantId(1L); row.setTaskId(10L); row.setAttemptId(20L);
        row.setDeviceId(30L); row.setRuleRevisionId(revisionId); return row;
    }

    private InspectionRuleQueryService.RuleView rule(long revisionId, String regex, CommandDraft... commands) {
        var view = new InspectionRuleQueryService.RuleView();
        view.setRevisionId(revisionId); view.setRevisionNo(1); view.setDetectionId("detection-" + revisionId);
        view.setInspectionItem("冻结规则" + revisionId); view.setExpectedResultRegex(regex);
        view.setThresholdOperator("≤"); view.setThresholdValue(BigDecimal.TEN); view.setThresholdUnit("ms");
        view.setCommands(List.of(commands)); return view;
    }

    private static CommandDraft command(String key, String text, int order) {
        return new CommandDraft(key, text, order, 30, false);
    }

    private static InspectionParserDocuments.Block block(int index, String command, String stdout) {
        return new InspectionParserDocuments.Block(index, command, "SUCCEEDED", stdout, "", false, 0);
    }

    private static DeviceLogParsingApi.NormalizedLog normalized(List<InspectionParserDocuments.Block> blocks, List<String> serials) {
        return new DeviceLogParsingApi.NormalizedLog(blocks.stream().map(block -> new DeviceLogParsingApi.LogBlock(
                block.commandIndex(), block.commandText(), block.stdout(), block.stderr(), block.status(), block.truncated(), block.exitCode())).toList(),
                serials, List.of());
    }

    private static DeviceLogParsingApi.NormalizedLog canonicalNormalization(DeviceLogParsingApi.LogInput input) {
        var document = JsonUtils.parseTree(input.inputText());
        if (!"1.0.0".equals(document.path("schemaVersion").asText())
                || !Set.copyOf(document.propertyNames()).equals(Set.of("schemaVersion", "commandBlocks"))
                || !document.path("commandBlocks").isArray()) {
            throw new DeviceLogParsingApi.InvalidInputException("模拟标准化器只接受canonical命令文档");
        }
        for (var block : document.path("commandBlocks")) {
            if (!Set.copyOf(block.propertyNames()).equals(Set.of("commandIndex", "commandText", "status",
                    "stdout", "stderr", "truncated", "exitCode"))) {
                throw new DeviceLogParsingApi.InvalidInputException("模拟标准化器拒绝非canonical命令字段");
            }
        }
        return normalized(InspectionParserDocuments.decode(bytes(input.inputText())).commandBlocks(), List.of("SELECTED-SN"));
    }

    private static byte[] collectionLog(List<InspectionParserDocuments.Block> blocks, boolean truncated) {
        return bytes(JsonUtils.toJsonString(collectionEnvelope(blocks.stream().map(
                InspectionExecutionServiceTest::collectionBlock).toList(), truncated)));
    }

    private static Map<String, Object> collectionEnvelope(List<?> blocks, boolean truncated) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", "synthetic-collection-event");
        envelope.put("namespace", "synthetic-inspection");
        envelope.put("externalRequestId", "collection-50");
        envelope.put("projectKey", "100");
        envelope.put("deviceKey", "synthetic-device-30");
        envelope.put("status", "SUCCEEDED");
        envelope.put("script", Map.of("sha256", "a".repeat(64)));
        envelope.put("startedAt", "2027-01-15T08:00:00Z");
        envelope.put("completedAt", "2027-01-15T08:00:00.050Z");
        envelope.put("truncated", truncated);
        envelope.put("commandBlocks", blocks);
        return envelope;
    }

    private static Map<String, Object> collectionBlock(InspectionParserDocuments.Block block) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("commandIndex", block.commandIndex()); source.put("commandText", block.commandText());
        source.put("status", block.status()); source.put("stdout", block.stdout()); source.put("stderr", block.stderr());
        source.put("truncated", block.truncated()); source.put("exitCode", block.exitCode());
        source.put("receivedBytes", 77); source.put("pageCount", 0);
        source.put("startedAt", 1_800_000_000_000L); source.put("completedAt", 1_800_000_000_050L);
        source.put("parsedFacts", Map.of("source", "synthetic")); source.put("parseWarnings", List.of());
        return source;
    }

    private static byte[] invalidCollectionLog(String defect) {
        var block = collectionBlock(block(1, "show latency", "OK"));
        var envelope = collectionEnvelope(List.of(block), false);
        switch (defect) {
            case "MISSING_BLOCKS" -> envelope.remove("commandBlocks");
            case "EMPTY_BLOCKS" -> envelope.put("commandBlocks", List.of());
            case "NULL_BLOCK" -> envelope.put("commandBlocks", Collections.singletonList(null));
            case "MISSING_ROOT_TRUNCATED" -> envelope.remove("truncated");
            case "MISSING_BLOCK_TRUNCATED" -> block.remove("truncated");
            case "MISSING_INDEX" -> block.remove("commandIndex");
            case "MISSING_TEXT" -> block.remove("commandText");
            case "MISSING_STATUS" -> block.remove("status");
            case "DUPLICATE_INDEX" -> envelope.put("commandBlocks", List.of(block, new LinkedHashMap<>(block)));
            case "ZERO_INDEX" -> block.put("commandIndex", 0);
            case "NEGATIVE_INDEX" -> block.put("commandIndex", -1);
            default -> throw new IllegalArgumentException("未知合成日志缺陷");
        }
        return bytes(JsonUtils.toJsonString(envelope));
    }

    private static Map<String, Object> unit(InspectionParserDocuments.Block block, String measuredValue) {
        return Map.of("commandIndex", block.commandIndex(), "commandText", block.commandText(), "nestingDepth", 0,
                "structureStatus", "STRUCTURED", "sections", List.of(Map.of("type", "keyValue",
                        "entries", List.of(Map.of("value", measuredValue)))));
    }

    private static DeviceLogParsingApi.ParseTask succeeded(InspectionParsePartDO job, List<Map<String, Object>> units) {
        return new DeviceLogParsingApi.ParseTask("parser-" + job.getRequestId(), "SUCCEEDED", null, "release-1", List.of(),
                Map.of("semanticResult", Map.of("genericContent", Map.of("units", units))));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
