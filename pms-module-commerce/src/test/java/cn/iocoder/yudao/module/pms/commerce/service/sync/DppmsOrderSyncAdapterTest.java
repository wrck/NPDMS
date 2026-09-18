package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.service.authority.AuthorityPayloadCanonicalizer;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Mock-only tests: no source database, credentials, or local application connection. */
class DppmsOrderSyncAdapterTest {
    private final ErpOrderSyncMapper mapper=mock(ErpOrderSyncMapper.class);
    private final CommerceAuthorityIngestApi authority=mock(CommerceAuthorityIngestApi.class);
    private final DppmsOrderSyncAdapter adapter=new DppmsOrderSyncAdapter(mapper,authority,new AuthorityPayloadCanonicalizer());
    private static final String ORDER_KEY="DPPMS|D365|01|1|R-1";
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void descriptorExposesStreamingCapability() {
        assertTrue(adapter.supportsStreaming());
        assertTrue(adapter.descriptor().supportsStreaming());
    }

    @Test void previewGroupsIdenticalHeadsAndDoesNotWrite() {
        var changes=adapter.preview(batch(row("LINE","3"),row("ORDER","1"),row("ORDER","2")));
        assertEquals(3,changes.size());
        assertTrue(changes.stream().allMatch(c->"CREATED".equals(c.action())));
        verifyNoInteractions(authority);
        verify(mapper).selectOrders(argThat(q->q.tenantId()==1 && q.orderNumbers().equals(List.of("R-1"))));
    }

    @Test void orderOnlyChunkNeverQueriesOrderLines() {
        when(mapper.selectOrders(any())).thenReturn(List.of(),List.of(order()));
        when(authority.ingestBatch(any())).thenAnswer(inv->{
            CommerceAuthorityBatchCommand command=inv.getArgument(0);
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });

        var changes=adapter.apply(batch(row("ORDER","1")));

        assertEquals(101L,changes.getFirst().targetId());
        verify(mapper,never()).selectLines(any());
    }

    @Test void applyUsesAuthorityApiForSignedReturnsAndMapsEveryOriginalId() {
        when(authority.ingestBatch(any())).thenAnswer(inv->{
            CommerceAuthorityBatchCommand command=inv.getArgument(0);
            assertEquals(1,command.salesOrders().size());
            assertEquals(1,command.orderLines().size());
            var line=command.orderLines().getFirst();
            assertEquals(new BigDecimal("-4"),line.orderQuantity());
            assertEquals(new BigDecimal("-3"),line.deliveredQuantity());
            assertNull(line.unitCode());
            assertEquals("PENDING_AUTHORITY",line.quantityStatus());
            assertEquals(CommerceSourceLifecycleStatus.RETURNED,line.lifecycleStatus());
            when(mapper.selectOrders(any())).thenReturn(List.of(order()));
            var stored=new SalesOrderLineDO();stored.setId(102L);stored.setSourceKey(ORDER_KEY+"|10");
            when(mapper.selectLines(any())).thenReturn(List.of(stored));
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        var changes=adapter.apply(batch(row("ORDER","1"),row("ORDER","2"),row("LINE","3")));
        assertEquals(List.of(101L,101L,102L),changes.stream().map(Change::targetId).toList());
    }

    @Test void missingParentsAndConflictingSourceGroupsAreExplicitIssues() {
        Row missing=row("LINE","3");
        var fields=new LinkedHashMap<>(missing.fields());fields.put("migrationIssue","DUPLICATE_BUSINESS_KEY_CONFLICT");
        var changes=adapter.preview(batch(missing,new Row("LINE","4",fields,null)));
        assertEquals(List.of("ISSUE","ISSUE"),changes.stream().map(Change::action).toList());
        assertTrue(changes.getFirst().message().contains("PARENT_ORDER_MISSING"));
        assertEquals("DUPLICATE_BUSINESS_KEY_CONFLICT",changes.getLast().message());
        verifyNoInteractions(authority);
    }

    @Test void existingForeignBusinessIdentityIsNotAdopted() {
        var existing=order();existing.setSourceKey("OTHER_SOURCE");
        when(mapper.selectOrders(any())).thenReturn(List.of(existing));
        assertEquals("CONFLICT",adapter.preview(batch(row("ORDER","1"))).getFirst().action());
        verifyNoInteractions(authority);
    }

    @Test void normalSalesStillRejectNegativeQuantities() {
        Row head=row("ORDER","1"),line=row("LINE","2");
        Map<String,Object> h=new LinkedHashMap<>(head.fields()),l=new LinkedHashMap<>(line.fields());
        h.put("orderType","0");l.put("orderType","0");
        var changes=adapter.preview(batch(new Row("ORDER","1",h,null),new Row("LINE","2",l,null)));
        assertEquals("ISSUE",changes.getLast().action());
        assertTrue(changes.getLast().message().contains("non-negative"));
    }
    @Test void pagedPreviewCanUseVerifiedSourceParentButApplyStillRequiresRealParent() {
        var line=row("LINE","8");var fields=new LinkedHashMap<>(line.fields());fields.put("migrationParentExists",1);
        var batch=batch(new Row("LINE","8",fields,null));
        assertEquals("CREATED",adapter.preview(batch).getFirst().action());
        assertEquals("ISSUE",adapter.apply(batch).getFirst().action());
        verifyNoInteractions(authority);
    }

    private static Batch batch(Row... rows) { return new Batch("integration:1:99",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false); }
    private static Row row(String object,String id) {
        Map<String,Object> fields=new LinkedHashMap<>(Map.of("erpSource","D365","companyCode","01","orderType","1",
                "orderNo","R-1","sourceUpdatedAt","2026-09-14T10:00:00"));
        if("LINE".equals(object))fields.putAll(Map.of("lineNo","10","orderQuantity",-4,"openQuantity",-1));
        return new Row(object,id,fields,null);
    }
    private static SalesOrderDO order() {
        var r=new SalesOrderDO();r.setId(101L);r.setTenantId(1L);r.setSourceKey(ORDER_KEY);
        r.setSourceSystem("ERP");r.setCompanyCode("01");r.setOrderType("1");r.setOrderNo("R-1");
        r.setSourceVersion("2026-09-14T10:00");r.setSourceUpdatedAt(LocalDateTime.of(2026,9,14,10,0));
        r.setSourceLifecycleStatus("RETURNED");r.setAuthorityStatus("CONFIRMED");return r;
    }
}