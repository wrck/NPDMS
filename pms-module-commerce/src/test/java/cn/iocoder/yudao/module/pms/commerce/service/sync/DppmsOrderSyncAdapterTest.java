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
    private final DppmsOrderFieldCarryOwner fieldCarry=mock(DppmsOrderFieldCarryOwner.class);
    private final DppmsOrderLineFieldCarryOwner lineFieldCarry=mock(DppmsOrderLineFieldCarryOwner.class);
    private final DppmsOrderSyncAdapter adapter=new DppmsOrderSyncAdapter(mapper,authority,new AuthorityPayloadCanonicalizer(),fieldCarry,lineFieldCarry);
    private static final String ORDER_KEY="DPPMS|D365|001|1|R-1";
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void descriptorExposesStreamingCapability() {
        assertTrue(adapter.supportsStreaming());
        assertTrue(adapter.descriptor().supportsStreaming());
    }

    @Test void reviewedLineCarryDelegatesToItsOwnerAndDoesNotEnterOrdinaryAuthority() {
        var input=batch(new Row("LINE","1",Map.of("lineFieldCarryProof",Map.of("targetId",20L)),20L));
        var expected=List.of(new Change("LINE","1",20L,"UNCHANGED",Map.of(),Map.of(),null));
        when(lineFieldCarry.preview(input)).thenReturn(expected);when(lineFieldCarry.apply(input)).thenReturn(expected);
        assertEquals(expected,adapter.preview(input));assertEquals(expected,adapter.apply(input));
        verifyNoInteractions(authority,mapper,fieldCarry);
    }

    @Test void descriptorExposesOrderChainColumns() {
        // 定义校验器按descriptor目标字段拒绝未知映射：链路列缺失会让含 contractNo/executionNo
        // 的任务定义无法保存，同步回填永远无法激活（2026-09-29 真实数据验收实测缺陷）。
        var order = adapter.descriptor().objects().stream()
                .filter(o -> "ORDER".equals(o.name())).findFirst().orElseThrow();
        var names = order.fields().stream().map(Field::name).toList();
        assertTrue(names.contains("contractNo"));
        assertTrue(names.contains("executionNo"));
    }

    @Test void reviewedFieldCarryUsesItsOwnerAndKeepsTheOrdinaryAuthorityPathClosed() {
        var old=order();old.setSourceVersion("opaque-existing-revision");old.setSourceUpdatedAt(LocalDateTime.parse("2026-09-14T10:00:00"));
        when(mapper.selectOrders(any())).thenReturn(List.of(old));
        var fields=new LinkedHashMap<>(row("ORDER","1").fields());
        fields.put("fieldCarryProof",Map.of("targetId",old.getId(),"expectedVersion",0L,"sourceFingerprint","a".repeat(64),"fieldsFingerprint","b".repeat(64),"protectedFingerprint","c".repeat(64)));
        fields.put("originalSourceFingerprint","a".repeat(64));fields.put("originalSourceFields","{\"id\":1,\"source\":\"D365\"}");
        var source=new Row("ORDER","1",fields,old.getId());var input=batch(source);
        when(fieldCarry.preview(eq(input),anyList())).thenAnswer(inv->{
            List<DppmsOrderFieldCarryOwner.Entry> entries=inv.getArgument(1);assertEquals(1,entries.size());
            assertEquals("opaque-existing-revision",entries.getFirst().fact().sourceVersion());assertEquals("opaque-existing-revision",entries.getFirst().fact().expectedPreviousSourceVersion());
            assertEquals("a".repeat(64),entries.getFirst().sourceFingerprint());assertEquals(1,entries.getFirst().originalFields().get("id"));return List.of();
        });
        assertTrue(adapter.preview(input).isEmpty());verify(fieldCarry).preview(eq(input),anyList());verifyNoInteractions(authority);
    }

    @Test void missingBoundTargetCannotEnterReviewedFieldCarry() {
        var fields=new LinkedHashMap<>(row("ORDER","1").fields());fields.put("fieldCarryProof",Map.of("targetId",101L,"expectedVersion",0));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(new Row("ORDER","1",fields,101L))));
        verifyNoInteractions(authority,fieldCarry);
    }

    @Test void conflictingSourceIsReportedWithoutEnteringReviewedFieldCarry() {
        var old=order();old.setSourceVersion("V");when(mapper.selectOrders(any())).thenReturn(List.of(old));
        var fields=new LinkedHashMap<>(row("ORDER","1").fields());fields.put("fieldCarryProof",Map.of("targetId",old.getId(),"expectedVersion",0));fields.put("migrationIssue","DUPLICATE_BUSINESS_KEY_CONFLICT");
        var input=batch(new Row("ORDER","1",fields,old.getId()));
        assertEquals("ISSUE",adapter.preview(input).getFirst().action());
        assertEquals("DUPLICATE_BUSINESS_KEY_CONFLICT",adapter.apply(input).getFirst().message());
        verifyNoInteractions(authority,fieldCarry);
    }

    @Test void missingProofDoesNotAbortOtherReviewedRowsOrEnterOrdinaryIngestion() {
        var good=new Row("LINE","1",Map.of("lineFieldCarryProof",Map.of("targetId",20L)),20L);
        var absent=new LinkedHashMap<String,Object>();absent.put("lineFieldCarryProof",null);
        var bad=new Row("LINE","2",absent,21L);
        var input=new Batch("integration:1:5",List.of(good,bad),List.of(),true,"RETAIN",false,"UPSERT",false);
        when(lineFieldCarry.preview(any())).thenAnswer(inv->{
            Batch selected=inv.getArgument(0);assertEquals(List.of(good),selected.rows());
            return List.of(new Change("LINE","1",20L,"UNCHANGED",Map.of(),Map.of(),null));
        });
        var result=adapter.preview(input);
        assertEquals(2,result.size());assertEquals("ISSUE",result.getFirst().action());
        assertTrue(result.getFirst().message().startsWith("FIELD_CARRY_PROOF_MISSING"));
        verifyNoInteractions(authority,mapper,fieldCarry);
        assertThrows(IllegalArgumentException.class,()->adapter.preview(new Batch("integration:1:5",
                List.of(good,row("ORDER","3")),List.of(),true,"RETAIN",false,"UPSERT",false)));
    }

    @Test void acceptedProductAndCustomFieldsReachTheAuthorityFacts() {
        when(authority.ingestBatch(any())).thenAnswer(inv->{
            CommerceAuthorityBatchCommand command=inv.getArgument(0);
            assertEquals("Head original custom",command.salesOrders().getFirst().customInfo());
            var line=command.orderLines().getFirst();
            assertEquals("PRODUCT-A",line.productCode());assertEquals("Original product description",line.productDesc());
            assertEquals("Line original custom",line.customInfo());
            when(mapper.selectOrders(any())).thenReturn(List.of(order()));
            var stored=new SalesOrderLineDO();stored.setId(102L);stored.setSourceKey(ORDER_KEY+"|10");
            when(mapper.selectLines(any())).thenReturn(List.of(stored));
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        var headFields=new LinkedHashMap<>(row("ORDER","1").fields());headFields.put("customInfo","Head original custom");
        var lineFields=new LinkedHashMap<>(row("LINE","2").fields());lineFields.putAll(Map.of("productCode","PRODUCT-A","productDesc","Original product description","customInfo","Line original custom"));
        var changes=adapter.apply(batch(new Row("ORDER","1",headFields,null),new Row("LINE","2",lineFields,null)));
        assertEquals(List.of(101L,102L),changes.stream().map(Change::targetId).toList());
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

    @Test void opaqueOwnerRevisionUsesSourceTimeAndPreservesPredecessor() {
        var stored=order();stored.setSourceVersion("migration-revision-1");
        stored.setSourceUpdatedAt(LocalDateTime.of(2026,9,13,10,0));
        when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        when(authority.ingestBatch(any())).thenAnswer(inv->{
            CommerceAuthorityBatchCommand command=inv.getArgument(0);
            assertEquals("migration-revision-1",command.salesOrders().getFirst().expectedPreviousSourceVersion());
            assertEquals(LocalDateTime.of(2026,9,14,10,0),command.salesOrders().getFirst().sourceUpdatedAt());
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        assertEquals("UPDATED",adapter.apply(batch(row("ORDER","1"))).getFirst().action());
        verify(authority).ingestBatch(any());
    }

    @Test void staleTimeCannotReplaceOpaqueOwnerRevision() {
        var stored=order();stored.setSourceVersion("migration-revision-1");
        stored.setSourceUpdatedAt(LocalDateTime.of(2026,9,15,10,0));
        when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        var change=adapter.preview(batch(row("ORDER","1"))).getFirst();
        assertEquals("ISSUE",change.action());
        assertTrue(change.message().contains("STALE_SOURCE_VERSION"));
        verifyNoInteractions(authority);
    }

    @Test void changedVersionWithoutSourceClockIsRejected() {
        var stored=order();stored.setSourceVersion("migration-revision-1");stored.setSourceUpdatedAt(null);
        when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        var change=adapter.preview(batch(row("ORDER","1"))).getFirst();
        assertEquals("ISSUE",change.action());
        assertTrue(change.message().contains("SOURCE_TIME_MISSING"));
        verifyNoInteractions(authority);
    }

    @Test void sameVersionPayloadConflictRemainsRejected() {
        var stored=order();stored.setContractNo("EXISTING-CONTRACT");
        when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        var change=adapter.preview(batch(row("ORDER","1"))).getFirst();
        assertEquals("ISSUE",change.action());
        assertTrue(change.message().contains("SOURCE_VERSION_PAYLOAD_CONFLICT"));
        verifyNoInteractions(authority);
    }

    @Test void opaqueLineRevisionUsesSourceTimeAndPreservesOwner() {
        var parent=order();when(mapper.selectOrders(any())).thenReturn(List.of(parent));
        var line=new SalesOrderLineDO();line.setId(102L);line.setOrderId(101L);line.setTenantId(1L);
        line.setSourceSystem("ERP");
        line.setSourceKey(ORDER_KEY+"|10");line.setSourceVersion("migration-line-revision-1");
        line.setSourceUpdatedAt(LocalDateTime.of(2026,9,13,10,0));
        when(mapper.selectLines(any())).thenReturn(List.of(line));
        when(authority.ingestBatch(any())).thenAnswer(inv->{
            CommerceAuthorityBatchCommand command=inv.getArgument(0);
            assertEquals("migration-line-revision-1",command.orderLines().getFirst().expectedPreviousSourceVersion());
            assertEquals(ORDER_KEY,command.orderLines().getFirst().salesOrderSourceKey());
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        assertEquals("UPDATED",adapter.apply(batch(row("LINE","2"))).getFirst().action());
        verify(authority).ingestBatch(any());
    }

    @Test void historicalErpCategoryIsCorrectedThroughTheOwnerEvenForAnUnchangedSourceVersion() {
        var stored=order();when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        when(authority.ingestBatch(any())).thenAnswer(invocation->{
            var command=invocation.getArgument(0,CommerceAuthorityBatchCommand.class);
            assertEquals("ERP",command.sourceSystem());assertEquals(ORDER_KEY,command.salesOrders().getFirst().sourceKey());
            stored.setSourceSystem("D365");return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        assertEquals("UPDATED",adapter.apply(batch(row("ORDER","1"))).getFirst().action());
        assertEquals("UNCHANGED",adapter.apply(batch(row("ORDER","1"))).getFirst().action());
        verify(authority,times(1)).ingestBatch(any());
    }
    @Test void wrongActualErpSystemCannotBeAdoptedByTheSameSourceKey() {
        var stored=order();stored.setSourceSystem("SMS");when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        assertEquals("CONFLICT",adapter.apply(batch(row("ORDER","1"))).getFirst().action());verifyNoInteractions(authority);
    }
    @Test void preservedRawCompanyOwnerKeyIsReusedByBothHeaderAndLineAfterCompanyConversion() {
        String oldKey="DPPMS|D365|01|1|R-1";var parent=order();parent.setSourceKey(oldKey);
        when(mapper.selectOrders(any())).thenReturn(List.of(parent));
        when(authority.ingestBatch(any())).thenAnswer(invocation->{
            var command=invocation.getArgument(0,CommerceAuthorityBatchCommand.class);
            assertEquals(oldKey,command.salesOrders().getFirst().sourceKey());
            assertEquals("001",command.salesOrders().getFirst().companyCode());
            assertEquals(oldKey,command.orderLines().getFirst().salesOrderSourceKey());
            assertEquals(oldKey+"|10",command.orderLines().getFirst().sourceKey());
            var line=new SalesOrderLineDO();line.setId(102L);line.setSourceKey(oldKey+"|10");
            when(mapper.selectLines(any())).thenReturn(List.of(line));
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        var changes=adapter.apply(batch(row("ORDER","1"),row("LINE","2")));
        assertEquals(List.of(101L,102L),changes.stream().map(Change::targetId).toList());
        assertEquals(List.of(oldKey,oldKey+"|10"),changes.stream().map(c->c.after().get("_targetSourceKey")).toList());
    }
    @Test void unknownCompanyBlocksBeforeAnyOwnerWrite() {
        var fields=new LinkedHashMap<>(row("ORDER","1").fields());fields.put("companyCode","05");
        var result=adapter.apply(batch(new Row("ORDER","1",fields,null))).getFirst();
        assertEquals("ISSUE",result.action());assertTrue(result.message().contains("DPPMS_COMPANY_CODE_UNMAPPED"));verifyNoInteractions(authority);
    }
    @Test void activeRawCompanyTargetMustNotBeMistakenForANewOrder() {
        var stored=order();stored.setCompanyCode("01");stored.setSourceKey("DPPMS|D365|01|1|R-1");
        when(mapper.selectOrders(any())).thenReturn(List.of(stored));
        var incoming=row("ORDER","1");incoming=new Row(incoming.object(),incoming.sourceKey(),incoming.fields(),101L);
        var change=adapter.preview(batch(incoming)).getFirst();
        assertEquals("ISSUE",change.action());
        assertTrue(change.message().contains("SOURCE_VERSION_PAYLOAD_CONFLICT"));
        verifyNoInteractions(authority);
    }
    @Test void twoActiveCompanyAliasesRemainAnUnresolvedBusinessCollision() {
        var raw=order();raw.setSourceKey("DPPMS|D365|01|1|R-1");raw.setCompanyCode("01");
        var converted=order();converted.setId(103L);
        when(mapper.selectOrders(any())).thenReturn(List.of(raw,converted));
        var change=adapter.preview(batch(row("ORDER","1"))).getFirst();
        assertEquals("CONFLICT",change.action());
        assertTrue(change.message().startsWith("DPPMS_ORDER_BUSINESS_IDENTITY_AMBIGUOUS"));
        verifyNoInteractions(authority);
    }
    @Test void ambiguousParentDoesNotHideOtherPreviewRowsAndBlocksTheEntireApplyBatch() {
        var raw=order();raw.setSourceKey("DPPMS|D365|01|1|R-1");raw.setCompanyCode("01");
        var converted=order();converted.setId(103L);
        var unrelated=new LinkedHashMap<>(row("ORDER","3").fields());unrelated.put("orderNo","R-2");
        var input=batch(row("ORDER","1"),row("LINE","2"),new Row("ORDER","3",unrelated,null));
        for (var targets:List.of(List.of(raw,converted),List.of(converted,raw))) {
            when(mapper.selectOrders(any())).thenReturn(targets);
            var preview=adapter.preview(input);
            assertEquals(3,preview.size());
            assertEquals(2,preview.stream().filter(c->"CONFLICT".equals(c.action())).count());
            assertEquals("CREATED",preview.stream().filter(c->"3".equals(c.sourceKey())).findFirst().orElseThrow().action());
            var applied=adapter.apply(input);
            assertEquals(preview,applied);
        }
        verifyNoInteractions(authority);
    }
    @Test void anExistingSourceBindingCannotBeRedirectedToAnotherOrder() {
        when(mapper.selectOrders(any())).thenReturn(List.of(order()));
        var incoming=row("ORDER","1");incoming=new Row(incoming.object(),incoming.sourceKey(),incoming.fields(),99L);
        var change=adapter.apply(batch(incoming)).getFirst();
        assertEquals("CONFLICT",change.action());
        verifyNoInteractions(authority);
    }
    @Test void deletedRawAliasDoesNotDisplaceItsExistingActiveIdentity() {
        var deleted=order();deleted.setId(99L);deleted.setSourceKey("DPPMS|D365|01|1|R-1");deleted.setCompanyCode("01");deleted.setDeleted(true);
        var active=order();when(mapper.selectOrders(any())).thenReturn(List.of(deleted,active));
        var incoming=row("ORDER","1");incoming=new Row(incoming.object(),incoming.sourceKey(),incoming.fields(),101L);
        var change=adapter.preview(batch(incoming)).getFirst();
        assertEquals("UPDATED",change.action());assertEquals(101L,change.targetId());
        assertEquals(ORDER_KEY,change.after().get("_targetSourceKey"));assertTrue(deleted.getDeleted());
        verifyNoInteractions(authority);
    }
    @Test void aSoleDeletedRawAliasCannotBecomeANewConvertedOrder() {
        var deleted=order();deleted.setSourceKey("DPPMS|D365|01|1|R-1");deleted.setCompanyCode("01");deleted.setDeleted(true);
        when(mapper.selectOrders(any())).thenReturn(List.of(deleted));
        assertEquals("CONFLICT",adapter.preview(batch(row("ORDER","1"))).getFirst().action());
        verifyNoInteractions(authority);
    }
    @Test void originalDescriptionAndEmptyBundleReachTheOwnerWithoutTrimmingOrDropping() {
        var fields=new LinkedHashMap<>(row("LINE","2").fields());fields.put("productDesc"," Description ");fields.put("bundleCode","");
        when(authority.ingestBatch(any())).thenAnswer(invocation->{
            var command=invocation.getArgument(0,CommerceAuthorityBatchCommand.class);var fact=command.orderLines().getFirst();
            assertEquals(" Description ",fact.productDesc());assertEquals("",fact.bundleCode());
            when(mapper.selectOrders(any())).thenReturn(List.of(order()));
            var line=new SalesOrderLineDO();line.setId(102L);line.setSourceKey(ORDER_KEY+"|10");when(mapper.selectLines(any())).thenReturn(List.of(line));
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        adapter.apply(batch(row("ORDER","1"),new Row("LINE","2",fields,null)));
    }
    @Test void originalDecimalQuantitiesRetainTheirPrecisionWithoutInventingAUnit() {
        var fields=new LinkedHashMap<>(row("LINE","2").fields());fields.put("orderQuantity","-4.125");fields.put("openQuantity","-1.05");
        when(mapper.selectOrders(any())).thenReturn(List.of(order()));
        when(authority.ingestBatch(any())).thenAnswer(invocation->{
            var command=invocation.getArgument(0,CommerceAuthorityBatchCommand.class);var fact=command.orderLines().getFirst();
            assertEquals(new BigDecimal("-4.125"),fact.orderQuantity());assertEquals(new BigDecimal("-3.075"),fact.deliveredQuantity());
            assertEquals(3,fact.unitScale());assertNull(fact.unitCode());assertEquals("PENDING_AUTHORITY",fact.quantityStatus());
            var line=new SalesOrderLineDO();line.setId(102L);line.setSourceKey(ORDER_KEY+"|10");when(mapper.selectLines(any())).thenReturn(List.of(line));
            return new CommerceAuthorityBatchResult(command.eventId(),command.batchId(),CommerceAuthorityBatchResult.Decision.ACCEPTED);
        });
        assertEquals("CREATED",adapter.apply(batch(new Row("LINE","2",fields,null))).getFirst().action());
    }
    @Test void approvedDppmsOriginalTextDoesNotRelaxOtherSourcesOrFieldLengthValidation() {
        var at=LocalDateTime.of(2020,1,2,3,4);
        assertThrows(cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.class,()->
                new CommerceOrderLineFact("OTHER-LINE",null,"V1","OTHER-ORDER","1","PRODUCT"," Description ",null,
                        BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,null,0,"PENDING_AUTHORITY",CommerceSourceLifecycleStatus.ACTIVE,
                        at,"0","",null,null,null,null));
        assertThrows(cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.class,()->
                new CommerceOrderLineFact(ORDER_KEY+"|1",null,"V1",ORDER_KEY,"1","PRODUCT","x".repeat(513),null,
                        BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,null,0,"PENDING_AUTHORITY",CommerceSourceLifecycleStatus.ACTIVE,
                        at,"0","",null,null,null,null));
    }

    private static Batch batch(Row... rows) { return new Batch("integration:1:99",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false); }
    private static Row row(String object,String id) {
        Map<String,Object> fields=new LinkedHashMap<>(Map.of("erpSource","D365","companyCode","001","orderType","1",
                "orderNo","R-1","sourceUpdatedAt","2026-09-14T10:00:00"));
        if("LINE".equals(object))fields.putAll(Map.of("lineNo","10","orderQuantity",-4,"openQuantity",-1));
        return new Row(object,id,fields,null);
    }
    private static SalesOrderDO order() {
        var r=new SalesOrderDO();r.setId(101L);r.setTenantId(1L);r.setSourceKey(ORDER_KEY);
        r.setSourceSystem("ERP");r.setCompanyCode("001");r.setOrderType("1");r.setOrderNo("R-1");
        r.setSourceVersion("2026-09-14T10:00");r.setSourceUpdatedAt(LocalDateTime.of(2026,9,14,10,0));
        r.setSourceLifecycleStatus("RETURNED");r.setAuthorityStatus("CONFIRMED");return r;
    }
}
