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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
        org.springframework.test.util.ReflectionTestUtils.setField(service,"deviceOrganizations",org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi.class));
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

    @Test void dppmsOrdersUseTheActualErpInTheExistingSourceSystemColumn() {
        when(salesOrderMapper.insertBatch(anyCollection(),eq(1000))).thenReturn(true);
        for (String source:List.of("SAP","D365","SMS")) {
            String key="DPPMS|"+source+"|001|0|ORDER";
            var fact=new CommerceSalesOrderFact(key,null,"V1","001","ORDER","0","CU-1","Customer",
                    new BigDecimal("100"),"CNY",CommerceSourceLifecycleStatus.ACTIVE,time());
            service.ingest(batch("actual-"+source,"batch-"+source,List.of(),List.of(fact),List.of()));
        }
        verify(salesOrderMapper,times(3)).insertBatch(argThat(rows->{
            var row=rows.iterator().next();return row.getSourceKey().startsWith("DPPMS|"+row.getSourceSystem()+"|")
                    && List.of("SAP","D365","SMS").contains(row.getSourceSystem());
        }),eq(1000));
    }
    @Test void unchangedDppmsOrderCorrectsOnlyTheErpCategoryAndReplaysWithoutRewriting() {
        String key="DPPMS|D365|001|0|ORDER";
        var current=orderRow(key,"V1");current.setCompanyCode("001");current.setOrderNo("ORDER");current.setOrderType("0");
        var fact=new CommerceSalesOrderFact(key,"V1","V1","001","ORDER","0","CU-1","Customer",
                new BigDecimal("100"),"CNY",CommerceSourceLifecycleStatus.ACTIVE,time());
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        when(salesOrderMapper.updateOwnerByVersion(any())).thenReturn(1);
        service.ingest(batch("correct-source","correct-batch",List.of(),List.of(fact),List.of()));
        assertEquals("D365",current.getSourceSystem());assertEquals(201L,current.getId());assertEquals("V1",current.getSourceVersion());
        service.ingest(batch("replay-source","replay-batch",List.of(),List.of(fact),List.of()));
        verify(salesOrderMapper,times(1)).updateOwnerByVersion(any());verify(salesOrderMapper,never()).insertBatch(anyCollection(),anyInt());
    }
    @Test void lineCanUseAnExistingActualErpParentWithoutCreatingAnotherOrder() {
        String key="DPPMS|D365|001|0|ORDER";
        var parent=orderRow(key,"V1");parent.setSourceSystem("D365");parent.setCompanyCode("001");parent.setOrderNo("ORDER");parent.setOrderType("0");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.insertBatch(anyCollection(),eq(1000))).thenReturn(true);
        service.ingest(batch("line-actual-parent","line-actual-batch",List.of(),List.of(),List.of(line(key+"|10",null,"V1",key,"3"))));
        verify(orderLineMapper).insertBatch(argThat(rows->rows.iterator().next().getOrderId().equals(201L)),eq(1000));
        verify(salesOrderMapper,never()).insertBatch(anyCollection(),anyInt());
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
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(order));
        when(salesOrderMapper.updateOwnerByVersion(any())).thenReturn(1);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-6", "B-6",
                List.of(contract("C-1", "V1", "V2", "ACME")),
                List.of(order("O-1", "V1", "V2")), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(contractMapper, never()).updateOwnerByVersion(any());
        verify(salesOrderMapper).updateOwnerByVersion(any());
        verify(salesOrderMapper, never()).selectBySourceForUpdate(any());
    }

    @Test
    void createsSalesOrdersWithOneBatchLockAndOneBatchInsert() {
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of());
        when(salesOrderMapper.insertBatch(anyCollection(), eq(1000))).thenReturn(true);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-BULK-O", "B-BULK-O", List.of(),
                List.of(order("O-1", null, "V1"), order("O-2", null, "V1")), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(salesOrderMapper, times(1)).selectBySourcesForUpdate(any());
        verify(salesOrderMapper, times(1)).insertBatch(argThat(rows -> rows.size() == 2), eq(1000));
        verify(salesOrderMapper, never()).insert(any(SalesOrderDO.class));
        verify(salesOrderMapper, never()).selectBySourceForUpdate(any());
    }

    @Test
    void orderCreateCarriesContractAndExecutionNoPhysicalColumns() {
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of());
        when(salesOrderMapper.insertBatch(anyCollection(), eq(1000))).thenReturn(true);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-O-CREATE", "B-O-CREATE", List.of(),
                List.of(orderWithChain("O-1", null, "V1", "CT-1", "EX-1")), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(salesOrderMapper).insertBatch(argThat(rows -> {
            SalesOrderDO row = rows.iterator().next();
            return "CT-1".equals(row.getContractNo()) && "EX-1".equals(row.getExecutionNo());
        }), eq(1000));
    }

    @Test
    void sameSourceVersionCannotChangeContractOrExecution() {
        var current=orderRowWithChain("O-1","V1");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        for (var incoming:List.of(orderWithChain("O-1","V1","V1","CT-2","EX-1"),
                orderWithChain("O-1","V1","V1","CT-1","EX-2"))) {
            var error=assertThrows(CommerceAuthorityIngestException.class,()->service.ingest(
                    batch("EV-CHAIN-CONFLICT","B-CHAIN-CONFLICT",List.of(),List.of(incoming),List.of())));
            assertEquals(SOURCE_VERSION_PAYLOAD_CONFLICT,error.getCode());
        }
        verify(salesOrderMapper,never()).updateOwnerByVersion(any());
    }

    @Test
    void idempotencyDigestIncludesContractAndExecution() {
        var canonicalizer=new AuthorityPayloadCanonicalizer();
        var original=batch("EV-CHAIN-DIGEST","B-CHAIN-DIGEST",List.of(),
                List.of(orderWithChain("O-1",null,"V1","CT-1","EX-1")),List.of());
        for (var incoming:List.of(orderWithChain("O-1",null,"V1","CT-2","EX-1"),
                orderWithChain("O-1",null,"V1","CT-1","EX-2"))) {
            assertNotEquals(canonicalizer.batchDigest(original),canonicalizer.batchDigest(
                    batch("EV-CHAIN-DIGEST","B-CHAIN-DIGEST",List.of(),List.of(incoming),List.of())));
        }
    }

    @Test
    void versionAdvanceRewritesOrderChainColumns() {
        SalesOrderDO current = orderRow("O-1", "V1");
        current.setContractNo("CT-OLD");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        when(salesOrderMapper.updateOwnerByVersion(any())).thenReturn(1);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-O-UPD", "B-O-UPD", List.of(),
                List.of(orderWithChain("O-1", "V1", "V2", "CT-1", "EX-1")), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(salesOrderMapper).updateOwnerByVersion(argThat(update -> {
            SalesOrderDO row = ((cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.SalesOrderAuthorityUpdate) update).row();
            return "CT-1".equals(row.getContractNo()) && "EX-1".equals(row.getExecutionNo());
        }));
    }

    @Test
    void replayRejectsChainColumnDriftWithoutOwnerWrite() {
        SalesOrderDO current = orderRowWithChain("O-1", "V2");
        current.setContractNo("CT-2");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));

        CommerceAuthorityIngestException error = assertThrows(CommerceAuthorityIngestException.class,
                () -> service.ingest(batch("EV-O-CONF", "B-O-CONF", List.of(),
                        List.of(orderWithChain("O-1", "V1", "V2", "CT-1", "EX-1")), List.of())));

        assertEquals(SOURCE_VERSION_PAYLOAD_CONFLICT, error.getCode());
        verify(salesOrderMapper, never()).updateOwnerByVersion(any());
    }

    @Test
    void acceptsOrderReplayWithMatchingChainColumns() {
        SalesOrderDO current = orderRowWithChain("O-1", "V2");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-O-REPLAY", "B-O-REPLAY", List.of(),
                List.of(orderWithChain("O-1", "V1", "V2", "CT-1", "EX-1")), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED_NO_CHANGE, result.decision());
        verify(salesOrderMapper, never()).updateOwnerByVersion(any());
    }

    @Test
    void staleCallerRowVersionRejectsUnchangedSourceTokenWithoutOverwritingTheConcurrentNote() {
        var current = orderRowWithChain("O-1", "V1");
        current.setVersion(7L);
        current.setOrderComment("Concurrent Owner note");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        var incoming = guardedOrder(orderWithChain("O-1", "V1", "V2", "CT-1", "EX-1"), 6L);

        var error = assertThrows(CommerceAuthorityIngestException.class, () -> service.ingest(
                batch("EV-ROW-STALE", "B-ROW-STALE", List.of(), List.of(incoming), List.of())));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        assertTrue(error.getMessage().startsWith("ROW_VERSION_CONFLICT:"));
        assertEquals("Concurrent Owner note", current.getOrderComment());
        assertEquals("V1", current.getSourceVersion());
        assertEquals(7L, current.getVersion());
        assertNull(commandApi.facts);
        verify(salesOrderMapper, never()).updateOwnerByVersion(any());
        verify(salesOrderMapper, never()).insertBatch(anyCollection(), anyInt());
    }

    @Test
    void staleCallerRowVersionCannotPassSameSourceVersionObjectReplay() {
        var current = orderRowWithChain("O-1", "V1");
        current.setVersion(7L);
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        var incoming = guardedOrder(orderWithChain("O-1", "V1", "V1", "CT-1", "EX-1"), 6L);

        var error = assertThrows(CommerceAuthorityIngestException.class, () -> service.ingest(
                batch("EV-ROW-REPLAY", "B-ROW-REPLAY", List.of(), List.of(incoming), List.of())));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        assertTrue(error.getMessage().startsWith("ROW_VERSION_CONFLICT:"));
        verify(salesOrderMapper, never()).updateOwnerByVersion(any());
    }

    @Test
    void matchingCallerRowVersionCorrectsDatesAndKeepsTheCurrentOwnerNote() {
        var current = orderRowWithChain("O-1", "V1");
        current.setVersion(7L);
        current.setOrderComment("Current Owner note");
        current.setOrderCreateTime(LocalDateTime.of(1970, 1, 1, 8, 0));
        current.setCustomerRequiredTime(current.getOrderCreateTime());
        current.setSourceUpdatedAt(current.getOrderCreateTime());
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        when(salesOrderMapper.updateOwnerByVersion(any())).thenReturn(1);
        var incoming = new CommerceSalesOrderFact("O-1", "V1", "V2", "ACME", "ON-1", "NORMAL",
                "CU-1", "Customer", new BigDecimal("100"), "CNY", CommerceSourceLifecycleStatus.ACTIVE,
                time(), "01", "源项目名", current.getOrderComment(), time(), time(), null, "CT-1", "EX-1", 7L);

        var result = service.ingest(batch("EV-ROW-CORRECT", "B-ROW-CORRECT", List.of(), List.of(incoming), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        assertEquals(time(), current.getOrderCreateTime());
        assertEquals(time(), current.getCustomerRequiredTime());
        assertEquals(time(), current.getSourceUpdatedAt());
        assertEquals("Current Owner note", current.getOrderComment());
        verify(salesOrderMapper).updateOwnerByVersion(argThat(update ->
                update.expectedVersion() == 7L && "V2".equals(update.row().getSourceVersion())));
    }

    @Test
    void omittedCallerRowVersionKeepsTheExistingSourcePredecessorContract() {
        var current = orderRowWithChain("O-1", "V1");
        current.setVersion(7L);
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        when(salesOrderMapper.updateOwnerByVersion(any())).thenReturn(1);
        var incoming = orderWithChain("O-1", "V1", "V2", "CT-1", "EX-1");
        assertNull(incoming.expectedRowVersion());

        var result = service.ingest(batch("EV-ROW-LEGACY", "B-ROW-LEGACY", List.of(), List.of(incoming), List.of()));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(salesOrderMapper).updateOwnerByVersion(argThat(update -> update.expectedVersion() == 7L));
    }

    @Test
    void callerRowVersionPreconditionCannotCreateAMissingCorrectionTarget() {
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of());
        var incoming = guardedOrder(orderWithChain("O-1", null, "V1", "CT-1", "EX-1"), 0L);

        var error = assertThrows(CommerceAuthorityIngestException.class, () -> service.ingest(
                batch("EV-ROW-MISSING", "B-ROW-MISSING", List.of(), List.of(incoming), List.of())));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        assertTrue(error.getMessage().startsWith("ROW_VERSION_CONFLICT:"));
        verify(salesOrderMapper, never()).insertBatch(anyCollection(), anyInt());
        verify(salesOrderMapper, never()).updateOwnerByVersion(any());
    }

    @Test
    void callerRowVersionIsExcludedFromTheCanonicalSourcePayloadAndBatchDigest() {
        var original = orderWithChain("O-1", "V1", "V2", "CT-1", "EX-1");
        var canonicalizer = new AuthorityPayloadCanonicalizer();
        for (var guarded : List.of(guardedOrder(original, 7L), guardedOrder(original, 8L))) {
            assertEquals(canonicalizer.orderPayload(original), canonicalizer.orderPayload(guarded));
            assertEquals(canonicalizer.batchDigest(batch("EV-ROW-DIGEST", "B-ROW-DIGEST", List.of(), List.of(original), List.of())),
                    canonicalizer.batchDigest(batch("EV-ROW-DIGEST", "B-ROW-DIGEST", List.of(), List.of(guarded), List.of())));
        }
    }

    @Test
    void staleLineCallerRowVersionRejectsSameSourceTokenAndPreservesConcurrentProductAndQuantity() {
        var parent = orderRow("O-1", "V1");
        var current = lineRowWithPayload(parent.getId(), "L-1", "V1", "17");
        current.setVersion(7);current.setProductCode("Concurrent Product");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        var incoming = guardedLine(line("L-1", "V1", "V2", "O-1", "10"), 6);

        var error = assertThrows(CommerceAuthorityIngestException.class, () -> service.ingest(
                batch("EV-L-ROW-STALE", "B-L-ROW-STALE", List.of(), List.of(), List.of(incoming))));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        assertTrue(error.getMessage().startsWith("ROW_VERSION_CONFLICT:"));
        assertEquals("Concurrent Product", current.getProductCode());
        assertEquals(new BigDecimal("17"), current.getQuantity());
        assertEquals("V1", current.getSourceVersion());assertEquals(7, current.getVersion());
        assertNull(commandApi.facts);
        verify(orderLineMapper, never()).updateOwnerByVersion(any());
        verify(orderLineMapper, never()).insertBatch(anyCollection(), anyInt());
        verifyNoInteractions(scopeImpactMapper);
    }

    @Test
    void staleLineCallerRowVersionCannotPassSameSourceVersionObjectReplay() {
        var parent = orderRow("O-1", "V1");
        var current = lineRowWithPayload(parent.getId(), "L-1", "V1", "10");current.setVersion(7);
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        var incoming = guardedLine(line("L-1", "V1", "V1", "O-1", "10"), 6);

        var error = assertThrows(CommerceAuthorityIngestException.class, () -> service.ingest(
                batch("EV-L-ROW-REPLAY", "B-L-ROW-REPLAY", List.of(), List.of(), List.of(incoming))));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        assertTrue(error.getMessage().startsWith("ROW_VERSION_CONFLICT:"));
        assertEquals("ITEM-1", current.getProductCode());assertEquals(new BigDecimal("10"), current.getQuantity());
        verify(orderLineMapper, never()).updateOwnerByVersion(any());verifyNoInteractions(scopeImpactMapper);
    }

    @Test
    void matchingLineCallerRowVersionCorrectsOnlySourceDateAndPreservesCurrentFacts() {
        var parent = orderRow("O-1", "V1");
        var current = lineRowWithPayload(parent.getId(), "L-1", "V1", "17");current.setVersion(7);
        current.setProductCode("Current Product");current.setProductDesc("Current Description");
        current.setProductModel("Current Model");current.setCustomInfo("{\"current\":true}");
        current.setBundleCode("Current Bundle");current.setProfitCenter("Current Profit");
        current.setRealExecutionNo("Current Execution");current.setWarrantyMonth(6);current.setLineType("0");
        current.setSourceUpdatedAt(LocalDateTime.of(1970, 1, 1, 8, 0));
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        when(orderLineMapper.updateOwnerByVersion(any())).thenReturn(1);
        var incoming = new CommerceOrderLineFact("L-1", "V1", "V2", "O-1", current.getLineCode(),
                current.getProductCode(), current.getProductDesc(), current.getProductModel(),
                current.getQuantity(), current.getOpenQty(), current.getDeliveredQty(), current.getUnitCode(),
                current.getUnitScale(), current.getQuantityStatus(), CommerceSourceLifecycleStatus.ACTIVE,
                time(), current.getLineType(), current.getBundleCode(), current.getProfitCenter(),
                current.getRealExecutionNo(), current.getWarrantyMonth(), current.getCustomInfo(), 7);

        var result = service.ingest(batch("EV-L-ROW-CORRECT", "B-L-ROW-CORRECT", List.of(), List.of(), List.of(incoming)));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        assertEquals(time(), current.getSourceUpdatedAt());assertEquals("Current Product", current.getProductCode());
        assertEquals("Current Description", current.getProductDesc());assertEquals("Current Model", current.getProductModel());
        assertEquals(new BigDecimal("17"), current.getQuantity());assertEquals(new BigDecimal("17"), current.getOpenQty());
        assertEquals(BigDecimal.ZERO, current.getDeliveredQty());assertEquals("{\"current\":true}", current.getCustomInfo());
        assertEquals("Current Bundle", current.getBundleCode());assertEquals("Current Profit", current.getProfitCenter());
        assertEquals("Current Execution", current.getRealExecutionNo());assertEquals(6, current.getWarrantyMonth());
        assertEquals(parent.getCompanyCode(), current.getCompanyCode());assertEquals(parent.getOrderNo(), current.getOrderNo());
        verify(orderLineMapper).updateOwnerByVersion(argThat(update ->
                update.expectedVersion() == 7 && "V2".equals(update.row().getSourceVersion())));
        verifyNoInteractions(scopeImpactMapper);
    }

    @Test
    void matchingLineCallerRowVersionAcceptsBusinessReplayWithoutWriting() {
        var parent = orderRow("O-1", "V1");
        var current = lineRowWithPayload(parent.getId(), "L-1", "V1", "10");current.setVersion(7);
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        var incoming = guardedLine(line("L-1", "V1", "V1", "O-1", "10"), 7);

        var result = service.ingest(batch("EV-L-ROW-SAME", "B-L-ROW-SAME", List.of(), List.of(), List.of(incoming)));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED_NO_CHANGE, result.decision());
        verify(orderLineMapper, never()).updateOwnerByVersion(any());verifyNoInteractions(scopeImpactMapper);
    }

    @Test
    void omittedLineCallerRowVersionKeepsTheExistingSourcePredecessorContract() {
        var parent = orderRow("O-1", "V1");
        var current = lineRowWithPayload(parent.getId(), "L-1", "V1", "10");current.setVersion(7);
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(current));
        when(orderLineMapper.updateOwnerByVersion(any())).thenReturn(1);
        var incoming = line("L-1", "V1", "V2", "O-1", "10");assertNull(incoming.expectedRowVersion());

        var result = service.ingest(batch("EV-L-ROW-LEGACY", "B-L-ROW-LEGACY", List.of(), List.of(), List.of(incoming)));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(orderLineMapper).updateOwnerByVersion(argThat(update -> update.expectedVersion() == 7));
    }

    @Test
    void lineCallerRowVersionPreconditionCannotCreateAMissingCorrectionTarget() {
        var parent = orderRow("O-1", "V1");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of());
        var incoming = guardedLine(line("L-1", null, "V1", "O-1", "10"), 0);

        var error = assertThrows(CommerceAuthorityIngestException.class, () -> service.ingest(
                batch("EV-L-ROW-MISSING", "B-L-ROW-MISSING", List.of(), List.of(), List.of(incoming))));

        assertEquals(SOURCE_VERSION_CONFLICT, error.getCode());
        assertTrue(error.getMessage().startsWith("ROW_VERSION_CONFLICT:"));
        verify(orderLineMapper, never()).insertBatch(anyCollection(), anyInt());
        verify(orderLineMapper, never()).updateOwnerByVersion(any());verifyNoInteractions(scopeImpactMapper);
    }

    @Test
    void lineCallerRowVersionIsExcludedFromCanonicalSourcePayloadAndBatchDigest() {
        var original = line("L-1", "V1", "V2", "O-1", "10");var canonicalizer = new AuthorityPayloadCanonicalizer();
        for (var guarded : List.of(guardedLine(original, 7), guardedLine(original, 8))) {
            assertEquals(canonicalizer.linePayload(original), canonicalizer.linePayload(guarded));
            assertEquals(canonicalizer.batchDigest(batch("EV-L-ROW-DIGEST", "B-L-ROW-DIGEST", List.of(), List.of(), List.of(original))),
                    canonicalizer.batchDigest(batch("EV-L-ROW-DIGEST", "B-L-ROW-DIGEST", List.of(), List.of(), List.of(guarded))));
        }
    }

    @Test
    void createsOrderLinesWithOneParentLockOneLineLockAndOneBatchInsert() {
        SalesOrderDO parent = orderRow("O-1", "V1");
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(parent));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of());
        when(orderLineMapper.insertBatch(anyCollection(), eq(1000))).thenReturn(true);

        CommerceAuthorityBatchResult result = service.ingest(batch("EV-BULK-L", "B-BULK-L", List.of(), List.of(),
                List.of(line("L-1", null, "V1", "O-1", "5"),
                        line("L-2", null, "V1", "O-1", "7"))));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(salesOrderMapper, times(1)).selectBySourcesForUpdate(any());
        verify(orderLineMapper, times(1)).selectBySourcesForUpdate(any());
        verify(orderLineMapper, times(1)).insertBatch(argThat(rows -> rows.size() == 2), eq(1000));
        verify(orderLineMapper, never()).insert(any(SalesOrderLineDO.class));
        verify(orderLineMapper, never()).selectBySourceForUpdate(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"DECREASE", "UNKNOWN_UNIT", "RETURN"})
    void quantityDecreaseOrUnconfirmedReturnFreezesActiveScopeAndIncrementsWatermark(String scenario) {
        SalesOrderDO order = orderRow("O-1", "V1");
        SalesOrderLineDO line = lineRow(order.getId(), "L-1", "V1", "10");
        DeliveryScopeDO active = activeScope(line.getId(), 701L, 901L, "8");
        DeliveryScopeDetailDO detail = activeDetail(active.getId(), "8");
        DeliveryScopeProjectVersionDO watermark = watermark(901L, 4L);
        when(salesOrderMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(order));
        when(orderLineMapper.selectBySourcesForUpdate(any())).thenReturn(List.of(line));
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

        CommerceOrderLineFact incoming = line("L-1", "V1", "V2", "O-1", "5");
        if (!"DECREASE".equals(scenario)) {
            incoming = new CommerceOrderLineFact("L-1", "V1", "V2", "O-1", "10", null, null, null,
                    new BigDecimal("RETURN".equals(scenario) ? "-4" : "12"), BigDecimal.ZERO, BigDecimal.ZERO,
                    null, 0, "PENDING_AUTHORITY", "RETURN".equals(scenario)
                    ? CommerceSourceLifecycleStatus.RETURNED : CommerceSourceLifecycleStatus.ACTIVE,
                    LocalDateTime.of(2026,8,30,12,0));
        }
        CommerceAuthorityBatchResult result = service.ingest(batch("EV-7", "B-7", List.of(),
                List.of(), List.of(incoming)));

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, result.decision());
        verify(scopeImpactMapper).insert(argThat((DeliveryScopeDO row) -> "CONFLICT_FROZEN".equals(row.getScopeStatus())
                && row.getAllocationVersion() == 2L && row.getAllocatedQty().compareTo(new BigDecimal("8")) == 0));
        verify(scopeImpactMapper).updateProjectVersionById(argThat(row -> row.getScopeVersion() == 5L
                && "SOURCE_CONFLICT".equals(row.getLastChangeType())));
    }

    private cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged genericLineChange(Long tenant,String quantity) {
        Map<String,Object> before=new LinkedHashMap<>();
        before.put("id",301L);before.put("tenant_id",1L);before.put("order_qty",new BigDecimal("10"));
        before.put("quantity_status","CONFIRMED");before.put("source_lifecycle_status","ACTIVE");
        before.put("source_system","ERP");before.put("source_record_key","L-1");before.put("source_version","V1");
        return new cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged(tenant,List.of(
                new cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged.Target("com_sales_order_line",before,
                        Map.of("order_qty",new BigDecimal(quantity),"source_version","V2"))));
    }
    private void prepareGenericScopeFreeze() {
        var active=activeScope(301L,701L,901L,"8");
        when(scopeImpactMapper.selectActiveScopesForUpdate(any())).thenReturn(List.of(active));
        when(scopeImpactMapper.selectDetailsForUpdate(any())).thenReturn(List.of(activeDetail(701L,"8")));
        when(scopeImpactMapper.releaseActiveScopeByVersion(any())).thenReturn(1);
        when(scopeImpactMapper.insert(any(DeliveryScopeDO.class))).thenAnswer(inv->{inv.<DeliveryScopeDO>getArgument(0).setId(702L);return 1;});
        when(scopeImpactMapper.insertScopeDetail(any())).thenReturn(1);
        when(scopeImpactMapper.selectProjectVersionForUpdate(any())).thenReturn(watermark(901L,4L));
        when(scopeImpactMapper.updateProjectVersionById(any())).thenReturn(1);
    }
    @Test void genericQuantityDecreaseFreezesScopeCopiesDetailsAndAdvancesWatermark() {
        prepareGenericScopeFreeze();service.onGenericTargetsChanged(genericLineChange(1L,"5"));
        verify(scopeImpactMapper).releaseActiveScopeByVersion(any());
        verify(scopeImpactMapper).insert(argThat((DeliveryScopeDO scope)->"CONFLICT_FROZEN".equals(scope.getScopeStatus())
                && scope.getAllocationVersion()==2L && scope.getAllocatedQty().compareTo(new BigDecimal("8"))==0));
        verify(scopeImpactMapper).insertScopeDetail(argThat(detail->detail.getDeliveryScopeId()==702L
                && detail.getAllocatedQty().compareTo(new BigDecimal("8"))==0 && "ITEM-1".equals(detail.getProductCode())));
        verify(scopeImpactMapper).updateProjectVersionById(argThat(version->version.getScopeVersion()==5L
                && "SOURCE_CONFLICT".equals(version.getLastChangeType())));
    }
    @Test void genericConfirmedQuantityIncreaseDoesNotTouchScopes() {
        service.onGenericTargetsChanged(genericLineChange(1L,"12"));verifyNoInteractions(scopeImpactMapper);
    }
    @Test void genericForeignTenantEventFailsBeforeScopeSideEffects() {
        assertThrows(CommerceAuthorityIngestException.class,()->service.onGenericTargetsChanged(genericLineChange(2L,"5")));
        verifyNoInteractions(scopeImpactMapper);
    }
    @Test void genericScopeWriteFailurePropagatesToThePublishingTransaction() {
        when(scopeImpactMapper.selectActiveScopesForUpdate(any())).thenReturn(List.of(activeScope(301L,701L,901L,"8")));
        when(scopeImpactMapper.releaseActiveScopeByVersion(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.onGenericTargetsChanged(genericLineChange(1L,"5")));
        verify(scopeImpactMapper,never()).insert(any(DeliveryScopeDO.class));
        verify(scopeImpactMapper,never()).insertScopeDetail(any());verify(scopeImpactMapper,never()).updateProjectVersionById(any());
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

    private CommerceSalesOrderFact orderWithChain(String key, String previous, String version,
                                                  String contractNo, String executionNo) {
        return new CommerceSalesOrderFact(key, previous, version, "ACME", "ON-1", "NORMAL",
                "CU-1", "Customer", new BigDecimal("100"), "CNY",
                CommerceSourceLifecycleStatus.ACTIVE, time(),
                "01", "源项目名", null, time(), time(), null, contractNo, executionNo);
    }

    private CommerceSalesOrderFact guardedOrder(CommerceSalesOrderFact fact, Long expectedRowVersion) {
        return new CommerceSalesOrderFact(fact.sourceKey(), fact.expectedPreviousSourceVersion(), fact.sourceVersion(),
                fact.companyCode(), fact.orderNo(), fact.orderType(), fact.customerCode(), fact.customerName(),
                fact.amount(), fact.currencyCode(), fact.lifecycleStatus(), fact.sourceUpdatedAt(), fact.salesType(),
                fact.sourceProjectName(), fact.orderComment(), fact.orderCreateTime(), fact.customerRequiredTime(),
                fact.customInfo(), fact.contractNo(), fact.executionNo(), expectedRowVersion);
    }

    private CommerceOrderLineFact guardedLine(CommerceOrderLineFact fact, Integer expectedRowVersion) {
        return new CommerceOrderLineFact(fact.sourceKey(), fact.expectedPreviousSourceVersion(), fact.sourceVersion(),
                fact.salesOrderSourceKey(), fact.lineCode(), fact.productCode(), fact.productDesc(), fact.productModel(),
                fact.orderQuantity(), fact.openQuantity(), fact.deliveredQuantity(), fact.unitCode(), fact.unitScale(),
                fact.quantityStatus(), fact.lifecycleStatus(), fact.sourceUpdatedAt(), fact.lineType(), fact.bundleCode(),
                fact.profitCenter(), fact.realExecutionNo(), fact.warrantyMonth(), fact.customInfo(), expectedRowVersion);
    }

    private SalesOrderLineDO lineRowWithPayload(Long orderId, String key, String version, String quantity) {
        var fact = line(key, null, version, "O-1", quantity);var row = lineRow(orderId, key, version, quantity);
        row.setOpenQty(fact.openQuantity());row.setDeliveredQty(fact.deliveredQuantity());
        row.setUnitScale(fact.unitScale());row.setQuantityStatus(fact.quantityStatus());
        return row;
    }

    private CommerceOrderLineFact line(String key, String previous, String version,
                                       String orderKey, String quantity) {
        return new CommerceOrderLineFact(key, previous, version, orderKey, "10", "ITEM-1",
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
        row.setVersion(0L);
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
        row.setSourceUpdatedAt(fact.sourceUpdatedAt()); row.setVersion(0L);
        row.setAuthorityStatus("CONFIRMED");
        row.setStatus("ENABLED");
        return row;
    }

    /** 与 orderWithChain 载荷镜像的存量行；回放比较要求逐字段一致。 */
    private SalesOrderDO orderRowWithChain(String key, String version) {
        CommerceSalesOrderFact fact = orderWithChain(key, null, version, "CT-1", "EX-1");
        SalesOrderDO row = orderRow(key, version);
        row.setSalesType(fact.salesType());
        row.setContractNo(fact.contractNo());
        row.setExecutionNo(fact.executionNo());
        row.setSourceProjectName(fact.sourceProjectName());
        row.setOrderComment(fact.orderComment());
        row.setOrderCreateTime(fact.orderCreateTime());
        row.setCustomerRequiredTime(fact.customerRequiredTime());
        return row;
    }

    private SalesOrderLineDO lineRow(Long orderId, String key, String version, String quantity) {
        SalesOrderLineDO row = new SalesOrderLineDO();
        row.setId(301L); row.setTenantId(1L); row.setOrderId(orderId); row.setSourceSystem("ERP");
        row.setSourceKey(key); row.setSourceVersion(version); row.setLineCode("10"); row.setProductCode("ITEM-1");
        row.setQuantity(new BigDecimal(quantity)); row.setUnitCode("PCS"); row.setSourceLifecycleStatus("ACTIVE");
        row.setSourceUpdatedAt(time()); row.setVersion(0);
        return row;
    }

    private DeliveryScopeDO activeScope(Long lineId, Long id, Long projectId, String quantity) {
        DeliveryScopeDO row = new DeliveryScopeDO();
        row.setId(id); row.setTenantId(1L); row.setOrderLineId(lineId); row.setProjectId(projectId);
        row.setAllocatedQty(new BigDecimal(quantity)); row.setScopeStatus("ACTIVE");
        row.setAllocationVersion(1L); row.setSourceEvidence("SRC"); row.setEffectiveFrom(time()); row.setVersion(0L);
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
