package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsOrderLineFieldCarryMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsOrderLineFieldCarryOwnerTest {
    final LocalDateTime clock=LocalDateTime.of(2026,8,2,23,51,27);
    final String sourceHash="a".repeat(64),beforeHash="b".repeat(64),protectedHash="c".repeat(64);
    DppmsOrderLineFieldCarryMapper mapper=mock(DppmsOrderLineFieldCarryMapper.class);
    ErpOrderSyncMapper identities=mock(ErpOrderSyncMapper.class);
    DppmsOrderLineFieldCarryOwner owner=new DppmsOrderLineFieldCarryOwner(mapper,identities);
    DppmsOrderLineFieldCarryMapper.Current current;SalesOrderDO parent;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);current=new DppmsOrderLineFieldCarryMapper.Current();
        current.setId(20L);current.setTenantId(1L);current.setOrderId(10L);current.setSourceKey("DPPMS|D365|01|1|ORDER|1");
        current.setSourceSystem("ERP");current.setSourceVersion("opaque-v4");current.setSourceUpdatedAt(clock);current.setVersion(0);current.setDeleted(false);
        current.setCompanyCode("01");current.setOrderType("1");current.setLineType("1");current.setOrderNo("ORDER");current.setLineNo("1");
        current.setProductCode("PRODUCT");current.setProductDesc(" 原描述 ");current.setProductId(88L);current.setProductModel("MODEL");
        current.setOrderQty(new BigDecimal("-2.000000"));current.setOpenQty(new BigDecimal("-0.25"));current.setDeliveredQty(new BigDecimal("-1.75"));
        current.setUnitScale(3);current.setQuantityStatus("PENDING_AUTHORITY");current.setSourceLifecycleStatus("RETURNED");current.setStatus("DISABLED");
        current.setCreator("old-creator");current.setUpdater("old-updater");current.setCreateTime(clock.minusYears(3));current.setUpdateTime(clock.minusYears(2));
        current.setFieldsFingerprint(beforeHash);current.setProtectedFingerprint(protectedHash);
        parent=new SalesOrderDO();parent.setId(10L);parent.setTenantId(1L);parent.setSourceKey("DPPMS|D365|001|1|ORDER");
        parent.setCompanyCode("001");parent.setCompanyId(1L);parent.setCompanyName("公司一");parent.setOrderType("1");parent.setOrderNo("ORDER");parent.setDeleted(false);
        when(mapper.selectCurrent(any())).thenAnswer(inv->List.of(current));when(mapper.selectCurrentForUpdate(any())).thenAnswer(inv->List.of(current));
        when(identities.selectOrders(any())).thenAnswer(inv->List.of(parent));when(identities.selectLines(any())).thenAnswer(inv->List.of(current));when(mapper.updateFields(any())).thenReturn(1);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();}
    Row row(String key) {
        Map<String,Object> raw=new LinkedHashMap<>();for(var name:List.of("id","orderNumber","lineNum","itemCode","itemDesc","orderQuantity","openQuantity",
                "bundleCode","warrantyMonth","lineType","compCode","profitCenter","realOrderExecNumber","source","customInfo","syncTime"))raw.put(name,null);
        raw.put("id",key);raw.put("orderNumber","ORDER");raw.put("lineNum","1");raw.put("itemCode","PRODUCT");raw.put("itemDesc"," 原描述 ");
        raw.put("orderQuantity",-2);raw.put("openQuantity",new BigDecimal("-0.25"));raw.put("bundleCode","");raw.put("warrantyMonth",36);raw.put("lineType",1);
        raw.put("compCode","01");raw.put("source","D365");raw.put("customInfo","{\"inventTransId\":\"RAW-TX\"}");raw.put("syncTime",clock.toString());
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("erpSource","D365");fields.put("companyCode","001");fields.put("orderNo","ORDER");fields.put("orderType",1);fields.put("lineNo","1");
        fields.put("productCode","PRODUCT");fields.put("productDesc"," 原描述 ");fields.put("orderQuantity",-2);fields.put("openQuantity",new BigDecimal("-0.25"));
        fields.put("bundleCode","");fields.put("warrantyMonth",36);fields.put("customInfo","{\"inventTransId\":\"RAW-TX\"}");fields.put("sourceUpdatedAt",clock.toString());
        raw.replaceAll((name,value)->value==null?tools.jackson.databind.node.NullNode.getInstance():value);
        fields.put("originalSourceFields",JsonUtils.toJsonString(raw));fields.put("originalSourceFingerprint",sourceHash);
        fields.put("lineFieldCarryProof",new DppmsOrderLineFieldCarryOwner.Proof(20L,0,sourceHash,beforeHash,protectedHash));
        return new Row("LINE",key,fields,20L);
    }
    Batch batch(Row... rows) {return new Batch("integration:1:100",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void previewRetainsEmptyBundleAndCompleteOriginalsWithoutWriting() {
        var change=owner.preview(batch(row("1"))).getFirst();assertEquals("UPDATED",change.action());assertEquals(20L,change.targetId());
        assertEquals(16,((Map<?,?>)change.after().get("originalFields")).size());verify(mapper,never()).updateFields(any());assertNull(current.getBundleCode());
    }
    @Test void applyPreservesSignedQuantitiesUnitAuthorityIdentityAuditAndForeignKeys() {
        when(mapper.updateFields(any())).thenAnswer(inv->{
            var update=(DppmsOrderLineFieldCarryMapper.FieldUpdate)inv.getArgument(0);var value=update.row();
            assertEquals("",value.getBundleCode());assertEquals("{\"inventTransId\":\"RAW-TX\"}",value.getCustomInfo());assertEquals(36,value.getWarrantyMonth());
            assertEquals("D365",value.getSourceSystem());assertEquals("001",value.getCompanyCode());assertEquals(1L,value.getCompanyId());
            assertEquals(current.getOrderId(),value.getOrderId());assertEquals(current.getSourceKey(),value.getSourceKey());assertEquals("opaque-v4",value.getSourceVersion());
            assertEquals(current.getOrderQty(),value.getOrderQty());assertEquals(current.getOpenQty(),value.getOpenQty());assertEquals(current.getDeliveredQty(),value.getDeliveredQty());
            assertNull(value.getUnitCode());assertEquals(3,value.getUnitScale());assertEquals("PENDING_AUTHORITY",value.getQuantityStatus());assertEquals("RETURNED",value.getSourceLifecycleStatus());
            assertEquals("DISABLED",value.getStatus());assertEquals(88L,value.getProductId());assertEquals("MODEL",value.getProductModel());
            assertEquals(current.getCreator(),value.getCreator());assertEquals(current.getUpdater(),value.getUpdater());assertEquals(current.getCreateTime(),value.getCreateTime());assertEquals(current.getUpdateTime(),value.getUpdateTime());
            return 1;
        });assertEquals("UPDATED",owner.apply(batch(row("1"))).getFirst().action());verify(mapper).updateFields(any());
    }
    @Test void fullyCarriedVersionReplaysWithNoSecondUpdate() {
        current.setVersion(1);current.setFieldsFingerprint("d".repeat(64));current.setSourceSystem("D365");current.setCompanyCode("001");current.setCompanyId(1L);current.setCompanyName("公司一");
        current.setBundleCode("");current.setCustomInfo("{\"inventTransId\": \"RAW-TX\"}");current.setWarrantyMonth(36);
        assertEquals("UNCHANGED",owner.apply(batch(row("1"))).getFirst().action());verify(mapper,never()).updateFields(any());
    }
    @Test void completeDecimalPrecisionIsVerifiedWithoutFloatingPointRounding() {
        var quantity=new BigDecimal("-999999999999.999999");current.setOrderQty(quantity);current.setOpenQty(BigDecimal.ZERO);current.setDeliveredQty(quantity);
        var original=row("1");var fields=new LinkedHashMap<>(original.fields());var raw=JsonUtils.parseMap(fields.get("originalSourceFields").toString());
        raw.put("orderQuantity",quantity);raw.put("openQuantity",BigDecimal.ZERO);raw.replaceAll((name,value)->value==null?tools.jackson.databind.node.NullNode.getInstance():value);
        fields.put("originalSourceFields",JsonUtils.toJsonString(raw));fields.put("orderQuantity",quantity);fields.put("openQuantity",BigDecimal.ZERO);
        assertEquals("UPDATED",owner.preview(batch(new Row("LINE","1",fields,20L))).getFirst().action());verify(mapper,never()).updateFields(any());
    }
    @Test void originalJsonObjectAndSourceJsonTextKeepTheSameCompleteContent() {
        var original=row("1");var fields=new LinkedHashMap<>(original.fields());var raw=JsonUtils.parseMap(fields.get("originalSourceFields").toString());
        raw.put("customInfo",Map.of("inventTransId","RAW-TX"));raw.replaceAll((name,value)->value==null?tools.jackson.databind.node.NullNode.getInstance():value);
        fields.put("originalSourceFields",JsonUtils.toJsonString(raw));fields.put("customInfo","{\"inventTransId\": \"RAW-TX\"}");
        assertEquals("UPDATED",owner.preview(batch(new Row("LINE","1",fields,20L))).getFirst().action());verify(mapper,never()).updateFields(any());
    }
    @Test void identicalOriginalsShareOneTargetWriteAndKeepBothSourceKeys() {
        var changes=owner.apply(batch(row("1"),row("2")));assertEquals(List.of("1","2"),changes.stream().map(Change::sourceKey).toList());verify(mapper,times(1)).updateFields(any());
    }
    @Test void protectedChangesWrongParentAndMissingBoundTargetsBlockBeforeAnyWrite() {
        for(String fault:List.of("protected","deleted","tenant","clock","quantity","parent","version","key")) {
            setup();switch(fault) {
                case "protected" -> current.setProtectedFingerprint("e".repeat(64));case "deleted" -> current.setDeleted(true);
                case "tenant" -> current.setTenantId(2L);case "clock" -> current.setSourceUpdatedAt(clock.plusSeconds(1));
                case "quantity" -> current.setOrderQty(new BigDecimal("2"));case "parent" -> current.setOrderId(11L);
                case "version" -> current.setVersion(2);case "key" -> current.setSourceKey("OTHER|KEY");
            }
            assertThrows(IllegalArgumentException.class,()->owner.apply(batch(row("1"))),fault);
        }
        when(mapper.selectCurrentForUpdate(any())).thenReturn(List.of());assertThrows(IllegalArgumentException.class,()->owner.apply(batch(row("1"))));verify(mapper,never()).updateFields(any());
    }
    @Test void changedEditableMappingAndChangedRawFingerprintAreRejected() {
        for(String field:List.of("productDesc","orderQuantity","originalSourceFingerprint")) {
            var original=row("1");var fields=new LinkedHashMap<>(original.fields());fields.put(field,field.equals("orderQuantity")?3:"CHANGED");
            assertThrows(IllegalArgumentException.class,()->owner.apply(batch(new Row("LINE","1",fields,20L))));
        }
        verifyNoInteractions(mapper);
    }
    @Test void nonemptyConflictingFieldsAndIncompleteReplayDoNotOverwrite() {
        current.setBundleCode("NATIVE-BUNDLE");assertThrows(IllegalArgumentException.class,()->owner.apply(batch(row("1"))));current.setBundleCode(null);
        current.setVersion(1);assertThrows(IllegalArgumentException.class,()->owner.apply(batch(row("1"))));verify(mapper,never()).updateFields(any());
    }
    @Test void normalizedCurrentParentCollisionIsStillBlocked() {
        var peer=new SalesOrderDO();org.springframework.beans.BeanUtils.copyProperties(parent,peer);peer.setId(11L);peer.setCompanyCode("01");
        when(identities.selectOrders(any())).thenReturn(List.of(parent,peer));assertThrows(IllegalArgumentException.class,()->owner.apply(batch(row("1"))));verify(mapper,never()).updateFields(any());
    }
    @Test void differentErpSystemsKeepIndependentOrdersAndLinesWithTheSameNumber() {
        var peerParent=new SalesOrderDO();org.springframework.beans.BeanUtils.copyProperties(parent,peerParent);
        peerParent.setId(11L);peerParent.setSourceKey("DPPMS|SAP|001|1|ORDER");
        var peerLine=new DppmsOrderLineFieldCarryMapper.Current();org.springframework.beans.BeanUtils.copyProperties(current,peerLine);
        peerLine.setId(21L);peerLine.setOrderId(11L);peerLine.setSourceKey("DPPMS|SAP|01|1|ORDER|1");
        when(identities.selectOrders(any())).thenReturn(List.of(parent,peerParent));
        when(identities.selectLines(any())).thenReturn(List.of(current,peerLine));
        assertEquals("UPDATED",owner.preview(batch(row("1"))).getFirst().action());verify(mapper,never()).updateFields(any());
    }
    @Test void normalizedCurrentLineCollisionWithinOneErpIsStillBlocked() {
        var peer=new DppmsOrderLineFieldCarryMapper.Current();org.springframework.beans.BeanUtils.copyProperties(current,peer);
        peer.setId(21L);peer.setCompanyCode("001");peer.setSourceKey("DPPMS|D365|001|1|ORDER|1");
        when(identities.selectLines(any())).thenReturn(List.of(current,peer));
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(row("1"))));verify(mapper,never()).updateFields(any());
    }
    @Test void foreignOwnerRebindingAndReplaceModesNeverReachTheMapper() {
        var input=row("1");for(var bad:List.of(new Batch("manual",List.of(input),List.of(),true,"RETAIN",false,"UPSERT",false),
                new Batch("integration:2:100",List.of(input),List.of(),true,"RETAIN",false,"UPSERT",false),
                new Batch("integration:1:100",List.of(input),List.of(),true,"RETAIN",true,"UPSERT",false),
                new Batch("integration:1:100",List.of(input),List.of(),true,"RETAIN",false,"UPSERT",true)))
            assertThrows(IllegalArgumentException.class,()->owner.apply(bad));
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(new Row("LINE","1",input.fields(),21L))));verifyNoInteractions(mapper);
    }
    @Test void aLostUpdateRemainsAFailure() {when(mapper.updateFields(any())).thenReturn(0);assertThrows(IllegalStateException.class,()->owner.apply(batch(row("1"))));}
}
