package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceAuthorityIngestServiceTest {

    @Mock private ContractAuthorityMapper contractMapper;
    @Mock private SalesOrderAuthorityMapper salesOrderMapper;
    @Mock private OrderLineAuthorityMapper orderLineMapper;
    @Mock private OrderContractRelationAuthorityMapper relationMapper;
    @Mock private AuthorityScopeImpactMapper scopeImpactMapper;
    @Mock private cn.iocoder.yudao.module.pms.commerce.service.scope.DeliveryScopeConflictNotifier conflictNotifier;

    private RecordingCommandApi commandApi;
    private CommerceAuthorityIngestService service;

    @BeforeEach
    void setUp() {
        commandApi = new RecordingCommandApi();
        service = new CommerceAuthorityIngestService(commandApi, new AuthorityPayloadCanonicalizer(),
                contractMapper, salesOrderMapper, orderLineMapper, relationMapper, scopeImpactMapper,
                conflictNotifier,
                Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsOwnerAndPersistsCorrelationFacts() {
        when(contractMapper.insert(any(ContractDO.class))).thenAnswer(invocation -> {
            invocation.<ContractDO>getArgument(0).setId(101L);
            return 1;
        });

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-1", "B-1",
                List.of(contract("C-1", null, "V1", "ACME")), List.of(), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        assertEquals("CORR-1", commandApi.facts.correlationId());
        assertEquals(64, commandApi.digest.length());
        verify(contractMapper).insert(argThat((ContractDO row) -> "CONFIRMED".equals(row.getAuthorityStatus())
                && "V1".equals(row.getSourceVersion()) && row.getTenantId() == 1L));
    }

    @Test
    void acceptsObjectReplayWithoutOwnerWrite() {
        ContractDO current = contractRow("C-1", "V2", "ACME");
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(current);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-2", "B-2",
                List.of(contract("C-1", "V1", "V2", "ACME")), List.of(), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED_NO_CHANGE, result.decision());
        verify(contractMapper, never()).updateOwnerByVersion(any());
    }

    @Test
    void rejectsSameVersionDifferentPayload() {
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(contractRow("C-1", "V2", "ACME"));

        CommerceAuthorityIngestException error = assertThrows(CommerceAuthorityIngestException.class,
                () -> service.ingest(batch("EV-3", "B-3",
                        List.of(contract("C-1", "V1", "V2", "OTHER")), List.of(), List.of())));

        assertEquals(SOURCE_VERSION_PAYLOAD_CONFLICT, error.getCode());
        assertNull(commandApi.facts);
    }

    @Test
    void rejectsWrongPredecessorBeforeUpdate() {
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(contractRow("C-1", "V2", "ACME"));

        CommerceAuthorityIngestException error = assertThrows(CommerceAuthorityIngestException.class,
                () -> service.ingest(batch("EV-4", "B-4",
                        List.of(contract("C-1", "V0", "V3", "ACME")), List.of(), List.of())));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        verify(contractMapper, never()).updateOwnerByVersion(any());
    }

    @Test
    void mapsEventReplayAndConflict() {
        commandApi.decision = PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED;
        CommerceAuthorityBatchResult replay = service.ingest(batch("EV-5", "B-5",
                List.of(contract("C-1", null, "V1", "ACME")), List.of(), List.of()));
        assertEquals(CommerceAuthorityBatchResult.Decision.EVENT_REPLAYED, replay.decision());

        commandApi.decision = PlatformCommandExecutionApi.Decision.CONFLICT;
        CommerceAuthorityIngestException error = assertThrows(CommerceAuthorityIngestException.class,
                () -> service.ingest(batch("EV-5", "B-5",
                        List.of(contract("C-1", null, "V1", "ACME")), List.of(), List.of())));
        assertEquals(EVENT_PAYLOAD_CONFLICT, error.getCode());
        verifyNoInteractions(contractMapper);
    }

    @Test
    void mixedBatchReplayAndUpdateReturnsAccepted() {
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(contractRow("C-1", "V2", "ACME"));
        SalesOrderDO order = orderRow("O-1", "V1");
        when(salesOrderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        when(salesOrderMapper.updateOwnerByVersion(any())).thenReturn(1);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-6", "B-6",
                List.of(contract("C-1", "V1", "V2", "ACME")),
                List.of(order("O-1", "V1", "V2")), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(contractMapper, never()).updateOwnerByVersion(any());
        verify(salesOrderMapper).updateOwnerByVersion(any());
    }

    @Test
    void quantityDecreaseFreezesActiveScopeAndIncrementsWatermark() {
        SalesOrderDO order = orderRow("O-1", "V1");
        SalesOrderLineDO line = lineRow(order.getId(), "L-1", "V1", "10");
        DeliveryScopeDO active = activeScope(line.getId(), 701L, 901L, "8");
        DeliveryScopeDetailDO detail = activeDetail(active.getId(), "8");
        DeliveryScopeProjectVersionDO watermark = watermark(901L, 4L);
        when(salesOrderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        when(orderLineMapper.selectBySourceForUpdate(any())).thenReturn(line);
        when(orderLineMapper.updateOwnerByVersion(any())).thenReturn(1);
        when(scopeImpactMapper.selectActiveScopesForUpdate(any())).thenReturn(List.of(active));
        when(scopeImpactMapper.selectDetailsForUpdate(any())).thenReturn(List.of(detail));
        when(scopeImpactMapper.releaseActiveScopeByVersion(any())).thenReturn(1);
        when(scopeImpactMapper.insert(any(DeliveryScopeDO.class))).thenAnswer(invocation -> {
            invocation.<DeliveryScopeDO>getArgument(0).setId(702L);
            return 1;
        });
        when(scopeImpactMapper.insertScopeDetail(any())).thenReturn(1);
        when(scopeImpactMapper.selectProjectVersionForUpdate(any())).thenReturn(watermark);
        when(scopeImpactMapper.updateProjectVersionById(any())).thenReturn(1);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-7", "B-7", List.of(),
                List.of(), List.of(line("L-1", "V1", "V2", "O-1", "5"))));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(scopeImpactMapper).insert(argThat((DeliveryScopeDO row) -> "CONFLICT_FROZEN".equals(row.getScopeStatus())
                && row.getAllocationVersion() == 2L && row.getAllocatedQty().compareTo(new BigDecimal("8")) == 0));
        verify(scopeImpactMapper).updateProjectVersionById(argThat(row -> row.getScopeVersion() == 5L
                && "SOURCE_CONFLICT".equals(row.getLastChangeType())));
    }

    private CommerceAuthorityBatchCommand batch(String eventId, String batchId,
                                                 List<CommerceContractFact> contracts,
                                                 List<CommerceSalesOrderFact> orders,
                                                 List<CommerceOrderLineFact> lines) {
        return new CommerceAuthorityBatchCommand(1L, eventId, batchId, "ERP", "WM-1",
                contracts, orders, lines, List.of(), time(), "CORR-1");
    }

    private CommerceContractFact contract(String key, String previous, String version, String company) {
        return new CommerceContractFact(key, previous, version, company, "CN-1", "CU-1", "Customer",
                new BigDecimal("100"), "CNY", CommerceSourceLifecycleStatus.ACTIVE, time());
    }

    private CommerceSalesOrderFact order(String key, String previous, String version) {
        return new CommerceSalesOrderFact(key, previous, version, "ACME", "ON-1", "NORMAL",
                "CU-1", "Customer", new BigDecimal("100"), "CNY",
                CommerceSourceLifecycleStatus.ACTIVE, time());
    }

    private CommerceOrderLineFact line(String key, String previous, String version,
                                       String orderKey, String quantity) {
        return new CommerceOrderLineFact(key, previous, version, orderKey, "10", "ITEM-1", null,
                new BigDecimal(quantity), "PCS", CommerceSourceLifecycleStatus.ACTIVE, time());
    }

    private ContractDO contractRow(String key, String version, String company) {
        CommerceContractFact fact = contract(key, null, version, company);
        ContractDO row = new ContractDO();
        row.setId(101L); row.setTenantId(1L); row.setSourceSystem("ERP"); row.setSourceKey(key);
        row.setSourceVersion(version); row.setCompanyCode(fact.companyCode()); row.setContractNo(fact.contractNo());
        row.setCustomerCode(fact.customerCode()); row.setCustomerName(fact.customerName());
        row.setContractAmount(fact.amount()); row.setCurrencyCode(fact.currencyCode());
        row.setSourceLifecycleStatus(fact.lifecycleStatus().name()); row.setSourceUpdatedAt(fact.sourceUpdatedAt());
        row.setVersion(0);
        return row;
    }

    private SalesOrderDO orderRow(String key, String version) {
        CommerceSalesOrderFact fact = order(key, null, version);
        SalesOrderDO row = new SalesOrderDO();
        row.setId(201L); row.setTenantId(1L); row.setSourceSystem("ERP"); row.setSourceKey(key);
        row.setSourceVersion(version); row.setCompanyCode(fact.companyCode()); row.setOrderNo(fact.orderNo());
        row.setOrderType(fact.orderType()); row.setCustomerCode(fact.customerCode());
        row.setCustomerName(fact.customerName()); row.setOrderAmount(fact.amount());
        row.setCurrencyCode(fact.currencyCode()); row.setSourceLifecycleStatus(fact.lifecycleStatus().name());
        row.setSourceUpdatedAt(fact.sourceUpdatedAt()); row.setVersion(0);
        return row;
    }

    private SalesOrderLineDO lineRow(Long orderId, String key, String version, String quantity) {
        SalesOrderLineDO row = new SalesOrderLineDO();
        row.setId(301L); row.setTenantId(1L); row.setOrderId(orderId); row.setSourceSystem("ERP");
        row.setSourceKey(key); row.setSourceVersion(version); row.setLineCode("10"); row.setItemCode("ITEM-1");
        row.setQuantity(new BigDecimal(quantity)); row.setUnitCode("PCS"); row.setSourceLifecycleStatus("ACTIVE");
        row.setSourceUpdatedAt(time()); row.setVersion(0);
        return row;
    }

    private DeliveryScopeDO activeScope(Long lineId, Long id, Long projectId, String quantity) {
        DeliveryScopeDO row = new DeliveryScopeDO();
        row.setId(id); row.setTenantId(1L); row.setOrderLineId(lineId); row.setProjectId(projectId);
        row.setAllocatedQty(new BigDecimal(quantity)); row.setScopeStatus("ACTIVE");
        row.setAllocationVersion(1L); row.setSourceEvidence("SRC"); row.setEffectiveFrom(time()); row.setVersion(0);
        return row;
    }

    private DeliveryScopeDetailDO activeDetail(Long scopeId, String quantity) {
        DeliveryScopeDetailDO row = new DeliveryScopeDetailDO();
        row.setId(801L); row.setTenantId(1L); row.setDeliveryScopeId(scopeId);
        row.setAllocatedQty(new BigDecimal(quantity)); row.setProductCode("ITEM-1");
        row.setDetailStatus("ACTIVE"); row.setVersion(0);
        return row;
    }

    private DeliveryScopeProjectVersionDO watermark(Long projectId, Long version) {
        DeliveryScopeProjectVersionDO row = new DeliveryScopeProjectVersionDO();
        row.setId(1001L); row.setTenantId(1L); row.setProjectId(projectId); row.setScopeVersion(version);
        row.setPayloadVersion(version.intValue()); row.setVersion(0);
        return row;
    }

    private LocalDateTime time() {
        return LocalDateTime.of(2026, 8, 30, 12, 0);
    }

    private static final class RecordingCommandApi implements PlatformCommandExecutionApi {
        private Decision decision = Decision.NEW;
        private SuccessFacts facts;
        private String digest;

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                              Class<T> responseType, Supplier<T> operation,
                                              Function<T, SuccessFacts> successFactsFactory) {
            this.digest = requestDigest;
            if (decision != Decision.NEW) {
                return new ExecutionResult<>(decision, null);
            }
            T response = operation.get();
            facts = successFactsFactory.apply(response);
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
