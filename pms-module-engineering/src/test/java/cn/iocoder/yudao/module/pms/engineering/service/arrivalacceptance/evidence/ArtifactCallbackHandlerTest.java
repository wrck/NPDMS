package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactAcceptedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactArchivedMessage;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceAcceptedUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceArchivedUpdate;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactCallbackHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-30T03:00:00Z"), ZoneOffset.UTC);

    @Mock DeliveryEvidenceMapper evidenceMapper;
    @Mock DeliveryEvidenceRevisionMapper revisionMapper;

    @Test
    void acceptsCurrentPublishedRevisionAndSchedulesArchiveAckWatermark() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("PUBLISHED_PENDING_ACC", null, 3));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());
        when(evidenceMapper.markAcceptedPendingArchiveIfMatch(any())).thenReturn(1);

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.APPLIED, result.outcome());
        assertEquals("ACCEPTED_PENDING_ARCHIVE", result.syncStatus());
        ArgumentCaptor<DeliveryEvidenceAcceptedUpdate> update =
                ArgumentCaptor.forClass(DeliveryEvidenceAcceptedUpdate.class);
        verify(evidenceMapper).markAcceptedPendingArchiveIfMatch(update.capture());
        assertEquals(LocalDateTime.of(2026, 8, 30, 3, 1), update.getValue().nextRetryAt());
        assertEquals(0, update.getValue().retryCount());
        assertEquals("review-1", update.getValue().reviewRecordId());
        assertCommand(commandApi, "ArtifactAccepted", "evt-a");
    }

    @Test
    void archivesOnlyAcceptedCurrentRevisionAndClearsRetryWatermark() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("ACCEPTED_PENDING_ARCHIVE", "review-1", 4));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());
        when(evidenceMapper.markArchivedIfMatch(any())).thenReturn(1);

        ArtifactCallbackResult result = handler(commandApi).handle(archived("evt-r", "archive-1"));

        assertEquals(ArtifactCallbackResult.Outcome.APPLIED, result.outcome());
        assertEquals("ARCHIVED", result.syncStatus());
        ArgumentCaptor<DeliveryEvidenceArchivedUpdate> update =
                ArgumentCaptor.forClass(DeliveryEvidenceArchivedUpdate.class);
        verify(evidenceMapper).markArchivedIfMatch(update.capture());
        assertEquals("archive-1", update.getValue().archiveRecordId());
        assertEquals("evt-r", update.getValue().eventId());
        assertCommand(commandApi, "ArtifactArchived", "evt-r");
    }

    @Test
    void replaysSameEventWithoutReadingBusinessRows() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        commandApi.decision = PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED;
        commandApi.replay = new ArtifactCallbackResult(
                ArtifactCallbackResult.Outcome.APPLIED, 50L, 1, "ACCEPTED_PENDING_ARCHIVE");

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(commandApi.replay, result);
        verifyNoInteractions(evidenceMapper, revisionMapper);
    }

    @Test
    void rejectsSameEventWithDifferentPayloadWithoutReadingBusinessRows() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        commandApi.decision = PlatformCommandExecutionApi.Decision.CONFLICT;

        assertThrows(IllegalStateException.class,
                () -> handler(commandApi).handle(accepted("evt-a", "review-2")));
        verifyNoInteractions(evidenceMapper, revisionMapper);
    }

    @Test
    void keepsAcceptedWaitingStateForSameReviewRecordWithoutRefreshingRetryTime() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("ARCHIVE_ACK_PENDING_RETRY", "review-1", 7));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-b", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.DUPLICATE, result.outcome());
        assertEquals("ARCHIVE_ACK_PENDING_RETRY", result.syncStatus());
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void keepsAcceptedPendingArchiveForSameReviewRecordWithoutBusinessWrite() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("ACCEPTED_PENDING_ARCHIVE", "review-1", 7));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-c", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.DUPLICATE, result.outcome());
        assertEquals("ACCEPTED_PENDING_ARCHIVE", result.syncStatus());
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void differentReviewRecordIsAuditedWithoutOverwritingProjection() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("ACCEPTED_PENDING_ARCHIVE", "review-1", 7));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-b", "review-2"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_MISMATCH, result.outcome());
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void archivedBeforeAcceptedIsAuditedWithoutStateChange() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("PUBLISHED_PENDING_ACC", null, 3));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());

        ArtifactCallbackResult result = handler(commandApi).handle(archived("evt-r", "archive-1"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_OUT_OF_ORDER, result.outcome());
        verify(evidenceMapper, never()).markArchivedIfMatch(any());
    }

    @Test
    void mismatchedRevisionArtifactAndFileVersionAreAuditOnly() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        DeliveryEvidenceRevisionDO mismatched = revision();
        mismatched.setFileArtifactId(41L);
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("PUBLISHED_PENDING_ACC", null, 3));
        when(revisionMapper.selectRevision(any())).thenReturn(mismatched);

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_MISMATCH, result.outcome());
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void tenantOrEvidenceMismatchIsAuditOnly() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(null);

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_MISMATCH, result.outcome());
        verifyNoInteractions(revisionMapper);
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void currentRevisionMismatchIsAuditOnly() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        DeliveryEvidenceDO root = evidence("PUBLISHED_PENDING_ACC", null, 3);
        root.setCurrentRevisionNo(2);
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(root);

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_MISMATCH, result.outcome());
        verifyNoInteractions(revisionMapper);
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void fileVersionMismatchIsAuditOnly() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        DeliveryEvidenceRevisionDO mismatched = revision();
        mismatched.setFileVersionNo(6);
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("PUBLISHED_PENDING_ACC", null, 3));
        when(revisionMapper.selectRevision(any())).thenReturn(mismatched);

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_MISMATCH, result.outcome());
        verify(evidenceMapper, never()).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void casMissReclassifiesLockedCurrentStateWithoutBlindOverwrite() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("PUBLISHED_PENDING_ACC", null, 3),
                evidence("ACCEPTED_PENDING_ARCHIVE", "review-1", 4));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());
        when(evidenceMapper.markAcceptedPendingArchiveIfMatch(any())).thenReturn(0);

        ArtifactCallbackResult result = handler(commandApi).handle(accepted("evt-a", "review-1"));

        assertEquals(ArtifactCallbackResult.Outcome.DUPLICATE, result.outcome());
        verify(evidenceMapper).markAcceptedPendingArchiveIfMatch(any());
    }

    @Test
    void invalidBoundaryMessageNeverClaimsInboxOrReadsBusinessRows() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        ArtifactAcceptedMessage invalid = new ArtifactAcceptedMessage(
                " ", 7L, 50L, 1, 40L, 5, "review-1",
                LocalDateTime.of(2026, 8, 30, 2, 0), "corr-1");

        assertThrows(IllegalArgumentException.class, () -> handler(commandApi).handle(invalid));
        assertEquals(0, commandApi.calls);
        verifyNoInteractions(evidenceMapper, revisionMapper);
    }

    @Test
    void digestIsLowercaseSha256AndChangesWithNormalizedPayload() {
        RecordingCommandApi first = new RecordingCommandApi();
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("ACCEPTED_PENDING_ARCHIVE", "review-1", 7));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());
        handler(first).handle(accepted("evt-b", "review-1"));

        RecordingCommandApi second = new RecordingCommandApi();
        handler(second).handle(accepted("evt-b", "review-2"));

        assertTrue(first.digest.matches("[0-9a-f]{64}"));
        assertTrue(second.digest.matches("[0-9a-f]{64}"));
        assertNotEquals(first.digest, second.digest);
    }

    @Test
    void publicMessagesExposeOnlyLockedCallbackFields() {
        Map<?, ?> accepted = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                        accepted("evt-a", "review-1")), Map.class);
        Map<?, ?> archived = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                        archived("evt-r", "archive-1")), Map.class);

        assertEquals(Set.of("eventId", "tenantId", "evidenceId", "evidenceRevision",
                "artifactId", "fileVersion", "reviewRecordId", "occurredAt", "correlationId"),
                accepted.keySet());
        assertEquals(Set.of("eventId", "tenantId", "evidenceId", "evidenceRevision",
                "artifactId", "fileVersion", "archiveRecordId", "occurredAt", "correlationId"),
                archived.keySet());
    }

    @Test
    void transactionalCompletionFailurePropagatesWithoutReportingCallbackSuccess() {
        RecordingCommandApi commandApi = new RecordingCommandApi();
        commandApi.failAfterOperation = true;
        when(evidenceMapper.selectByIdentityForUpdate(any())).thenReturn(
                evidence("PUBLISHED_PENDING_ACC", null, 3));
        when(revisionMapper.selectRevision(any())).thenReturn(revision());
        when(evidenceMapper.markAcceptedPendingArchiveIfMatch(any())).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> handler(commandApi).handle(accepted("evt-a", "review-1")));
        assertEquals(1, commandApi.calls);
    }

    private ArtifactCallbackHandler handler(RecordingCommandApi commandApi) {
        return new ArtifactCallbackHandler(evidenceMapper, revisionMapper, commandApi, CLOCK);
    }

    private static ArtifactAcceptedMessage accepted(String eventId, String recordId) {
        return new ArtifactAcceptedMessage(eventId, 7L, 50L, 1, 40L, 5, recordId,
                LocalDateTime.of(2026, 8, 30, 2, 0), "corr-1");
    }

    private static ArtifactArchivedMessage archived(String eventId, String recordId) {
        return new ArtifactArchivedMessage(eventId, 7L, 50L, 1, 40L, 5, recordId,
                LocalDateTime.of(2026, 8, 30, 2, 0), "corr-1");
    }

    private static DeliveryEvidenceDO evidence(String status, String acceptedRecordId, int version) {
        DeliveryEvidenceDO evidence = new DeliveryEvidenceDO();
        evidence.setId(50L);
        evidence.setTenantId(7L);
        evidence.setCurrentRevisionNo(1);
        evidence.setAccSyncStatus(status);
        evidence.setAccAcceptedRecordId(acceptedRecordId);
        evidence.setVersion(version);
        return evidence;
    }

    private static DeliveryEvidenceRevisionDO revision() {
        DeliveryEvidenceRevisionDO revision = new DeliveryEvidenceRevisionDO();
        revision.setTenantId(7L);
        revision.setEvidenceId(50L);
        revision.setRevisionNo(1);
        revision.setFileArtifactId(40L);
        revision.setFileVersionNo(5);
        return revision;
    }

    private static void assertCommand(RecordingCommandApi api, String eventType, String eventId) {
        assertEquals("IMP:ARRIVAL_EVIDENCE_CALLBACK:" + eventType, api.scope.scopeCode());
        assertEquals(0L, api.scope.actorId());
        assertEquals(eventId, api.scope.key());
        assertTrue(api.digest.matches("[0-9a-f]{64}"));
    }

    private static final class RecordingCommandApi implements PlatformCommandExecutionApi {
        private Decision decision = Decision.NEW;
        private ArtifactCallbackResult replay;
        private IdempotencyScope scope;
        private String digest;
        private int calls;
        private boolean failAfterOperation;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                               Class<T> responseType, Supplier<T> operation,
                                               Function<T, SuccessFacts> successFactsFactory) {
            calls++;
            this.scope = scope;
            this.digest = requestDigest;
            if (decision == Decision.NEW) {
                T response = operation.get();
                if (failAfterOperation) {
                    throw new IllegalStateException("platform transaction completion failed");
                }
                successFactsFactory.apply(response);
                return new ExecutionResult<>(decision, response);
            }
            return new ExecutionResult<>(decision, (T) replay);
        }
    }
}
