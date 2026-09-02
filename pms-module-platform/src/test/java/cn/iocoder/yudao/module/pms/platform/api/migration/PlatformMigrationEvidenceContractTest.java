package cn.iocoder.yudao.module.pms.platform.api.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PlatformMigrationEvidenceContractTest {

    private static final String SHA256 = "a".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 20, 0);

    @Test
    void exposesEightWriteActionsAndOneCursorQuery() {
        Set<String> methods = Arrays.stream(PlatformMigrationEvidenceApi.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("createImportBatch", "appendSourceRecord", "markStagedReady",
                "claimStagedBatch", "pageSourceRecords", "appendExternalMapping",
                "appendMigrationIssue", "completeReconciliation", "closeMigrationIssue"), methods);
    }

    @Test
    void validatesNormalizedBatchIdentityAndManifestFacts() {
        PlatformMigrationEvidenceException whitespace = assertThrows(
                PlatformMigrationEvidenceException.class,
                () -> new CreateImportBatchCommand(1L, " COM", "F-COM-001", "R1", "ERP",
                        "orders", "1", 1, SHA256, NOW, null, null, "key", "corr"));
        PlatformMigrationEvidenceException checksum = assertThrows(
                PlatformMigrationEvidenceException.class,
                () -> new CreateImportBatchCommand(1L, "COM", "F-COM-001", "R1", "ERP",
                        "orders", "1", 1, "ABC", NOW, null, null, "key", "corr"));

        assertEquals(PlatformMigrationEvidenceException.Code.INVALID_REQUEST, whitespace.getCode());
        assertEquals(PlatformMigrationEvidenceException.Code.INVALID_REQUEST, checksum.getCode());
    }

    @Test
    void keepsSourcePayloadOpaqueAndIdentityComplete() {
        AppendMigrationSourceRecordCommand command = new AppendMigrationSourceRecordCommand(
                1L, 2L, "ERP", "orders", "PK-1", null, " {\"raw\":1} ", SHA256,
                NOW, "corr");

        assertEquals(" {\"raw\":1} ", command.sourcePayloadJson());
        assertEquals("PK-1", command.sourcePk());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new AppendMigrationSourceRecordCommand(
                        1L, 2L, "ERP", "orders", "PK-1", null, "not-json", SHA256,
                        NOW, "corr"));
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new MigrationSourceRecordPageQuery(1L, 2L, null, 501));
    }

    @Test
    void locksReadyAndTerminalImportFailureAsStrictUnion() {
        MarkStagedReadyCommand ready = new MarkStagedReadyCommand(
                1L, 2L, 3, ImportStagingDecision.READY,
                10L, "1", SHA256, null, "key-ready", "corr");
        MarkStagedReadyCommand failed = new MarkStagedReadyCommand(
                1L, 2L, 3, ImportStagingDecision.FAIL_IMPORT,
                null, null, null, MigrationImportFailureCode.SOURCE_RECORD_CONFLICT,
                "key-failed", "corr");

        assertEquals(10L, ready.manifestRowCount());
        assertEquals(MigrationImportFailureCode.SOURCE_RECORD_CONFLICT, failed.failureCode());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new MarkStagedReadyCommand(
                        1L, 2L, 3, ImportStagingDecision.READY,
                        10L, "1", SHA256, MigrationImportFailureCode.SOURCE_PAYLOAD_INVALID,
                        "key", "corr"));
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new MarkStagedReadyCommand(
                        1L, 2L, 3, ImportStagingDecision.FAIL_IMPORT,
                        10L, "1", SHA256, MigrationImportFailureCode.MANIFEST_ROW_COUNT_MISMATCH,
                        "key", "corr"));
        assertInvalidRequest(() -> new MarkStagedReadyCommand(
                1L, 2L, 3, ImportStagingDecision.READY,
                null, "1", SHA256, null, "key", "corr"));
        assertInvalidRequest(() -> new MarkStagedReadyCommand(
                1L, 2L, 3, ImportStagingDecision.READY,
                10L, null, SHA256, null, "key", "corr"));
        assertInvalidRequest(() -> new MarkStagedReadyCommand(
                1L, 2L, 3, ImportStagingDecision.READY,
                10L, "1", null, null, "key", "corr"));
        assertInvalidRequest(() -> new MarkStagedReadyCommand(
                1L, 2L, 3, ImportStagingDecision.FAIL_IMPORT,
                null, null, null, null, "key", "corr"));
    }

    @Test
    void locksMappedAndRetainedAsStrictUnion() {
        ExternalTargetMapping later = new ExternalTargetMapping(
                "COM", "ORDER", "com_sales_order", 22L, "PRIMARY", 1);
        ExternalTargetMapping first = new ExternalTargetMapping(
                "COM", "ORDER", "com_sales_order", 21L, "PRIMARY", 0);
        AppendExternalMappingCommand mapped = new AppendExternalMappingCommand(
                1L, 2L, 3L, SourceReconciliationType.MAPPED, List.of(later, first),
                "key", "corr");
        AppendExternalMappingCommand retained = new AppendExternalMappingCommand(
                1L, 2L, 4L, SourceReconciliationType.RETAINED, List.of(), "key-2", "corr");

        assertEquals(List.of(first, later), mapped.targets());
        assertTrue(retained.targets().isEmpty());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new AppendExternalMappingCommand(1L, 2L, 3L,
                        SourceReconciliationType.MAPPED, List.of(), "key", "corr"));
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new AppendExternalMappingCommand(1L, 2L, 3L,
                        SourceReconciliationType.RETAINED, List.of(first), "key", "corr"));
    }

    @Test
    void requiresCompleteCountsBeforeCompletion() {
        CompleteReconciliationCommand command = new CompleteReconciliationCommand(
                1L, 2L, 3, 10, 4, 5, 1, "rules-v1", "key", "corr");

        assertEquals(10, command.expectedSourceCount());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new CompleteReconciliationCommand(
                        1L, 2L, 3, 10, 4, 4, 1, "rules-v1", "key", "corr"));
    }

    @Test
    void separatesInputErrorsFromOwnerOutputCorruption() {
        PlatformMigrationEvidenceException input = assertThrows(
                PlatformMigrationEvidenceException.class,
                () -> new CloseMigrationIssueCommand(
                        1L, 2L, 3L, " rules", "{}", "key", "corr"));
        PlatformMigrationEvidenceException output = assertThrows(
                PlatformMigrationEvidenceException.class,
                () -> new MigrationBatchFact(1L, 1L, "COM", "F-COM-001", "R1", "ERP",
                        "orders", MigrationBatchStatus.COMPLETED, 2, 1, 0, 0,
                        null, 1, NOW));

        assertEquals(PlatformMigrationEvidenceException.Code.INVALID_REQUEST, input.getCode());
        assertEquals(PlatformMigrationEvidenceException.Code.OWNER_DATA_CORRUPTED, output.getCode());
    }

    @Test
    void requiresClosedIssueResolutionAndKeepsOpenIssueEmpty() {
        MigrationIssueFact open = new MigrationIssueFact(
                1L, 1L, 2L, 3L, "ISSUE-1", "MISSING_QUANTITY",
                MigrationIssueStatus.OPEN, null, null, null, null);
        MigrationIssueFact closed = new MigrationIssueFact(
                1L, 1L, 2L, 3L, "ISSUE-1", "MISSING_QUANTITY",
                MigrationIssueStatus.CLOSED, 9L, "rules-v2", "{\"result\":\"retained\"}", NOW);

        assertEquals(MigrationIssueStatus.OPEN, open.status());
        assertEquals(9L, closed.resolverUserId());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new MigrationIssueFact(
                        1L, 1L, 2L, 3L, "ISSUE-1", "MISSING_QUANTITY",
                        MigrationIssueStatus.CLOSED, null, null, null, null));
    }

    @Test
    void distinguishesEmptyClaimFromReconciliationClaim() {
        MigrationBatchFact claimedBatch = new MigrationBatchFact(
                2L, 1L, "COM", "F-COM-001", "R1", "ERP", "orders",
                MigrationBatchStatus.RECONCILING, 10, 0, 0, 0,
                null, 2, NOW);

        assertFalse(MigrationBatchClaimResult.empty().claimed());
        assertTrue(new MigrationBatchClaimResult(true, claimedBatch).claimed());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new MigrationBatchClaimResult(false, claimedBatch));
    }

    @Test
    void keepsClaimVersionStableUntilSingleCompletionCas() {
        MigrationBatchFact claimedBatch = new MigrationBatchFact(
                2L, 1L, "COM", "F-COM-001", "R1", "ERP", "orders",
                MigrationBatchStatus.RECONCILING, 10, 0, 0, 0,
                null, 4, NOW);
        CompleteReconciliationCommand completion = new CompleteReconciliationCommand(
                1L, 2L, claimedBatch.version(), 10, 4, 5, 1,
                "rules-v1", "key", "corr");

        assertEquals(4, completion.expectedBatchVersion());
        assertThrows(PlatformMigrationEvidenceException.class,
                () -> new MigrationBatchFact(
                        2L, 1L, "COM", "F-COM-001", "R1", "ERP", "orders",
                        MigrationBatchStatus.RECONCILING, 10, 1, 0, 0,
                        null, 4, NOW));
    }

    @Test
    void exposesDistinctBatchIdentitySourceAndStateFailures() {
        assertTrue(Set.of(PlatformMigrationEvidenceException.Code.values())
                .containsAll(Set.of(
                        PlatformMigrationEvidenceException.Code.BATCH_SOURCE_IDENTITY_MISMATCH,
                        PlatformMigrationEvidenceException.Code.SOURCE_NOT_FOUND,
                        PlatformMigrationEvidenceException.Code.BATCH_STATE_CONFLICT)));
    }

    private static void assertInvalidRequest(org.junit.jupiter.api.function.Executable executable) {
        PlatformMigrationEvidenceException exception = assertThrows(
                PlatformMigrationEvidenceException.class, executable);
        assertEquals(PlatformMigrationEvidenceException.Code.INVALID_REQUEST, exception.getCode());
    }
}
