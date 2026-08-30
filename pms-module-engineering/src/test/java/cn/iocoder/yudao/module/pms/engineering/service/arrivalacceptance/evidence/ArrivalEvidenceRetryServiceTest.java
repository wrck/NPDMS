package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryUpdate;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalEvidenceRetryServiceTest {

    @Mock DeliveryEvidenceMapper evidenceMapper;
    @Mock DeliveryEvidenceRevisionMapper revisionMapper;
    @Mock ArrivalAcceptanceMapper acceptanceMapper;
    @Mock ArrivalEvidenceEventFactory eventFactory;
    private RecordingCommandApi commandApi;
    private ArrivalEvidenceRetryService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        commandApi = new RecordingCommandApi();
        service = new ArrivalEvidenceRetryService(evidenceMapper, revisionMapper, acceptanceMapper,
                commandApi, eventFactory,
                Clock.fixed(Instant.parse("2026-08-30T02:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void firstAcceptedWaitTimeoutOnlyEntersRetryState() {
        DeliveryEvidenceDO root = root("PUBLISHED_PENDING_ACC", 0);
        when(evidenceMapper.selectNextDueForRetry(any())).thenReturn(root);
        when(evidenceMapper.advanceRetryIfMatch(any())).thenReturn(1);

        assertTrue(service.retryNext(LocalDateTime.of(2026, 8, 30, 2, 0)));

        ArgumentCaptor<DeliveryEvidenceRetryUpdate> update =
                ArgumentCaptor.forClass(DeliveryEvidenceRetryUpdate.class);
        verify(evidenceMapper).advanceRetryIfMatch(update.capture());
        assertEquals("ARCHIVE_PENDING_RETRY", update.getValue().targetStatus());
        assertEquals(0, update.getValue().expectedRetryCount());
        assertEquals(1, update.getValue().newRetryCount());
        assertEquals(LocalDateTime.of(2026, 8, 30, 2, 1), update.getValue().nextRetryAt());
        assertNull(update.getValue().eventId());
        verify(eventFactory, never()).nextEventId();
        assertTrue(commandApi.successFacts.businessEvents().isEmpty());
    }

    @Test
    void publishRetryQueuesFrozenRevisionWithOriginalCorrelation() {
        DeliveryEvidenceDO root = root("ARCHIVE_PENDING_RETRY", 1);
        when(evidenceMapper.selectNextDueForRetry(any())).thenReturn(root);
        when(revisionMapper.selectRevision(any())).thenReturn(revision());
        when(acceptanceMapper.selectRow(any())).thenReturn(acceptance());
        when(eventFactory.nextEventId()).thenReturn("retry-event-1");
        when(eventFactory.published(any())).thenAnswer(invocation -> {
            ImplementationEvidencePublishedMessage message = invocation.getArgument(0);
            return new PlatformCommandExecutionApi.BusinessEvent(
                    message.eventId(), "ImplementationEvidencePublished", "payload");
        });
        when(evidenceMapper.advanceRetryIfMatch(any())).thenReturn(1);

        service.retryNext(LocalDateTime.of(2026, 8, 30, 2, 0));

        ArrivalEvidenceRetryService.ArrivalEvidenceRetryResult result = commandApi.result;
        assertEquals("PUBLISHED_PENDING_ACC", result.status());
        assertEquals("retry-event-1", result.eventId());
        assertEquals("corr-original", result.message().correlationId());
        assertEquals("scope-frozen", result.message().sourceScopeWatermark());
        assertEquals(900L, result.message().sourceRecordId());
        assertEquals(LocalDateTime.of(2026, 8, 30, 2, 0), result.message().occurredAt());
        assertEquals(1, commandApi.successFacts.businessEvents().size());
        assertEquals(64, commandApi.digest.length());
        assertEquals("50:1:ARCHIVE_PENDING_RETRY:1", commandApi.scope.key());
    }

    @Test
    void archivedWaitUsesSeparateRetryStateBeforeQueueing() {
        DeliveryEvidenceDO root = root("ACCEPTED_PENDING_ARCHIVE", 0);
        when(evidenceMapper.selectNextDueForRetry(any())).thenReturn(root);
        when(evidenceMapper.advanceRetryIfMatch(any())).thenReturn(1);

        service.retryNext(LocalDateTime.of(2026, 8, 30, 2, 0));

        ArgumentCaptor<DeliveryEvidenceRetryUpdate> update =
                ArgumentCaptor.forClass(DeliveryEvidenceRetryUpdate.class);
        verify(evidenceMapper).advanceRetryIfMatch(update.capture());
        assertEquals("ARCHIVE_ACK_PENDING_RETRY", update.getValue().targetStatus());
        assertNull(update.getValue().eventId());
        verify(revisionMapper, never()).selectRevision(any());
    }

    @Test
    void emptyDueSetStopsBatch() {
        when(evidenceMapper.selectNextDueForRetry(any())).thenReturn(null);

        assertEquals(false, service.retryNext(LocalDateTime.of(2026, 8, 30, 2, 0)));

        assertEquals(0, commandApi.calls);
    }

    @Test
    void backoffUsesPreUpdateCountAndCapsAtSixtyMinutes() {
        assertEquals(1, ArrivalEvidenceRetryService.delayMinutes(0));
        assertEquals(2, ArrivalEvidenceRetryService.delayMinutes(1));
        assertEquals(4, ArrivalEvidenceRetryService.delayMinutes(2));
        assertEquals(8, ArrivalEvidenceRetryService.delayMinutes(3));
        assertEquals(16, ArrivalEvidenceRetryService.delayMinutes(4));
        assertEquals(32, ArrivalEvidenceRetryService.delayMinutes(5));
        assertEquals(60, ArrivalEvidenceRetryService.delayMinutes(6));
        assertEquals(60, ArrivalEvidenceRetryService.delayMinutes(20));
    }

    private static DeliveryEvidenceDO root(String status, int retryCount) {
        DeliveryEvidenceDO root = new DeliveryEvidenceDO();
        root.setId(50L);
        root.setTenantId(7L);
        root.setSourceRequirement("EXE-01");
        root.setSourceObjectType("ARRIVAL_ACCEPTANCE");
        root.setSourceObjectId(900L);
        root.setCurrentRevisionNo(1);
        root.setAccSyncStatus(status);
        root.setAccRetryCount(retryCount);
        root.setAccNextRetryAt(LocalDateTime.of(2026, 8, 30, 1, 59));
        root.setAccCorrelationId("corr-original");
        root.setVersion(4);
        return root;
    }

    private static DeliveryEvidenceRevisionDO revision() {
        DeliveryEvidenceRevisionDO revision = new DeliveryEvidenceRevisionDO();
        revision.setEvidenceId(50L);
        revision.setTenantId(7L);
        revision.setRevisionNo(1);
        revision.setFileArtifactId(40L);
        revision.setFileVersionNo(5);
        revision.setFileReferenceId("REF-1");
        revision.setFileHash("hash");
        revision.setSourceRecordId(900L);
        revision.setSourceVersion(3L);
        return revision;
    }

    private static ArrivalAcceptanceDO acceptance() {
        ArrivalAcceptanceDO acceptance = new ArrivalAcceptanceDO();
        acceptance.setId(900L);
        acceptance.setTenantId(7L);
        acceptance.setStatus("CONFIRMED");
        acceptance.setEvidenceId(50L);
        acceptance.setEvidenceRevision(1);
        acceptance.setScopeWatermark("scope-frozen");
        return acceptance;
    }

    private static final class RecordingCommandApi implements PlatformCommandExecutionApi {
        private int calls;
        private IdempotencyScope scope;
        private String digest;
        private SuccessFacts successFacts;
        private ArrivalEvidenceRetryService.ArrivalEvidenceRetryResult result;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                              Class<T> responseType, Supplier<T> operation,
                                              Function<T, SuccessFacts> successFactsFactory) {
            calls++;
            this.scope = scope;
            this.digest = requestDigest;
            T response = operation.get();
            this.result = (ArrivalEvidenceRetryService.ArrivalEvidenceRetryResult) response;
            this.successFacts = successFactsFactory.apply(response);
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
