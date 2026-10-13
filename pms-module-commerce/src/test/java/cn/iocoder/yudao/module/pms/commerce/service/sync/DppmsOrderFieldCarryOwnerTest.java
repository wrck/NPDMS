package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsOrderFieldCarryMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.service.migration.DppmsExecutionOrganizationReferences;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsOrderFieldCarryOwnerTest {
    final String sourceHash="a".repeat(64),beforeHash="b".repeat(64),protectedHash="c".repeat(64),afterHash="d".repeat(64);
    final LocalDateTime clock=LocalDateTime.of(2026,7,1,10,20,30);
    DppmsOrderFieldCarryMapper mapper=mock(DppmsOrderFieldCarryMapper.class);
    ErpOrderSyncMapper identities=mock(ErpOrderSyncMapper.class);
    DppmsExecutionOrganizationReferences organizations=mock(DppmsExecutionOrganizationReferences.class);
    DppmsOrderFieldCarryOwner owner=new DppmsOrderFieldCarryOwner(mapper,identities,organizations);
    DppmsOrderFieldCarryMapper.Current current;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);current=current();
        when(mapper.selectCurrent(any())).thenAnswer(inv->List.of(current));
        when(mapper.selectCurrentForUpdate(any())).thenAnswer(inv->List.of(current));
        when(identities.selectOrders(any())).thenAnswer(inv->List.of(current));
        var company=mock(ManagedOrganizationApi.Node.class);when(company.id()).thenReturn(1L);when(company.name()).thenReturn("公司一");
        when(organizations.load()).thenReturn(new DppmsExecutionOrganizationReferences.Directory(Map.of("001",company),Map.of()));
        when(mapper.updateFields(any())).thenReturn(1);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    DppmsOrderFieldCarryMapper.Current current() {
        var row=new DppmsOrderFieldCarryMapper.Current();row.setId(20L);row.setTenantId(1L);row.setVersion(0L);row.setDeleted(false);
        row.setSourceKey("DPPMS|D365|01|0|ORDER");row.setSourceSystem("ERP");row.setSourceVersion(clock.toString());row.setSourceUpdatedAt(clock);
        row.setCompanyCode("01");row.setOrderNo("ORDER");row.setOrderType("0");row.setCustomerCode("CUSTOMER");
        row.setStatus("DISABLED");row.setSourceLifecycleStatus("ACTIVE");row.setOrderAmount(new BigDecimal("123.00"));row.setCurrencyCode("CNY");
        row.setCreator("original-creator");row.setUpdater("original-updater");row.setCreateTime(clock.minusDays(3));row.setUpdateTime(clock.minusDays(2));
        row.setFieldsFingerprint(beforeHash);row.setProtectedFingerprint(protectedHash);return row;
    }
    DppmsOrderFieldCarryOwner.Entry entry(String id,String contract,String comment) {
        Map<String,Object> raw=new LinkedHashMap<>();for(var key:List.of("id","orderNumber","contractNo","orderExecNumber","orderCreateTime",
                "customerRequireTime","customerCode","customerName","projectName","orderComment","orderType","compCode","salesType","source","customInfo","syncTime"))raw.put(key,null);
        raw.put("id",id);raw.put("source","D365");raw.put("contractNo",contract);raw.put("orderComment",comment);
        raw.put("orderNumber","ORDER");raw.put("orderType",0);raw.put("compCode","01");raw.put("customerCode","CUSTOMER");raw.put("orderExecNumber","EXEC");
        var row=new Row("ORDER",id,Map.of(),20L);
        var fact=new CommerceSalesOrderFact(current.getSourceKey(),clock.toString(),clock.toString(),"001","ORDER","0","CUSTOMER",null,null,null,
                CommerceSourceLifecycleStatus.ACTIVE,clock,null,null,comment,null,null,null,contract,"EXEC");
        var proof=new DppmsOrderFieldCarryOwner.Proof(20L,0L,sourceHash,beforeHash,protectedHash);
        return new DppmsOrderFieldCarryOwner.Entry(row,fact,proof,sourceHash,raw);
    }
    Batch batch(DppmsOrderFieldCarryOwner.Entry... entries) { return new Batch("integration:1:100",Arrays.stream(entries).map(DppmsOrderFieldCarryOwner.Entry::source).toList(),List.of(),true,"RETAIN",false,"UPSERT",false); }

    @Test void previewCarriesMissingChainAndOrganizationWithoutWriting() {
        var e=entry("1","CONTRACT","备注");var changes=owner.preview(batch(e),List.of(e));
        assertEquals("UPDATED",changes.getFirst().action());assertEquals(20L,changes.getFirst().targetId());
        assertEquals(16,((Map<?,?>)changes.getFirst().after().get("originalFields")).size());
        verify(mapper,never()).updateFields(any());assertEquals("01",current.getCompanyCode());assertNull(current.getContractNo());
    }
    @Test void applyChangesOnlyApprovedFieldsAndReturnsThePersistedFingerprint() {
        var e=entry("1","CONTRACT"," 原备注 ");
        when(mapper.updateFields(any())).thenAnswer(inv->{
            var update=(DppmsOrderFieldCarryMapper.FieldUpdate)inv.getArgument(0);var row=update.row();
            assertEquals(0L,update.expectedVersion());assertEquals("CONTRACT",row.getContractNo());assertEquals("EXEC",row.getExecutionNo());
            assertEquals(" 原备注 ",row.getOrderComment());assertEquals("D365",row.getSourceSystem());assertEquals("001",row.getCompanyCode());assertEquals(1L,row.getCompanyId());
            assertEquals("DISABLED",row.getStatus());assertEquals("ACTIVE",row.getSourceLifecycleStatus());assertEquals("CNY",row.getCurrencyCode());
            assertEquals(new BigDecimal("123.00"),row.getOrderAmount());assertEquals(current.getSourceVersion(),row.getSourceVersion());
            assertEquals(current.getSourceUpdatedAt(),row.getSourceUpdatedAt());assertEquals(current.getCreateTime(),row.getCreateTime());assertEquals(current.getUpdateTime(),row.getUpdateTime());
            assertEquals(current.getCreator(),row.getCreator());assertEquals(current.getUpdater(),row.getUpdater());
            current.setFieldsFingerprint(afterHash);current.setVersion(1L);return 1;
        });
        var change=owner.apply(batch(e),List.of(e)).getFirst();assertEquals("UPDATED",change.action());assertEquals(afterHash,change.after().get("fieldCarryTargetFingerprint"));
        verify(mapper).updateFields(any());
    }
    @Test void duplicateOriginalsRetainIndependentResultsAndUpdateTheirTargetOnce() {
        var a=entry("1","CONTRACT","原备注");var b=entry("2","CONTRACT","原备注");
        var changes=owner.apply(batch(a,b),List.of(a,b));assertEquals(List.of("1","2"),changes.stream().map(Change::sourceKey).toList());
        assertEquals(List.of(20L,20L),changes.stream().map(Change::targetId).toList());verify(mapper,times(1)).updateFields(any());
    }
    @Test void onlyExactWhitespaceLossIsRestoredAndDifferentBusinessContentBlocks() {
        current.setOrderComment("原备注");var e=entry("1","CONTRACT"," 原备注 ");
        assertEquals("UPDATED",owner.preview(batch(e),List.of(e)).getFirst().action());
        current.setOrderComment("其他正式备注");assertThrows(IllegalArgumentException.class,()->owner.apply(batch(e),List.of(e)));
        verify(mapper,never()).updateFields(any());
    }
    @Test void completeCarriedVersionReplaysWithoutAnotherUpdate() {
        current.setVersion(1L);current.setFieldsFingerprint(afterHash);current.setCompanyCode("001");current.setCompanyId(1L);current.setCompanyName("公司一");
        current.setSourceSystem("D365");current.setContractNo("CONTRACT");current.setExecutionNo("EXEC");current.setOrderComment("备注");
        var e=entry("1","CONTRACT","备注");assertEquals("UNCHANGED",owner.apply(batch(e),List.of(e)).getFirst().action());verify(mapper,never()).updateFields(any());
    }
    @Test void incompleteDifferentVersionCannotBeMisreportedAsAReplay() {
        current.setVersion(1L);current.setFieldsFingerprint(afterHash);var e=entry("1","CONTRACT","备注");
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(e),List.of(e)));verify(mapper,never()).updateFields(any());
    }
    @Test void changedProtectedFieldsForeignIdentityDeletedAndStaleClockBlock() {
        for(String fault:List.of("protected","identity","deleted","clock","tenant","version")) {
            current=current();switch(fault) {
                case "protected" -> current.setProtectedFingerprint("e".repeat(64));
                case "identity" -> current.setSourceKey("OTHER|ORDER");
                case "deleted" -> current.setDeleted(true);
                case "clock" -> current.setSourceUpdatedAt(clock.plusSeconds(1));
                case "tenant" -> current.setTenantId(2L);
                case "version" -> current.setVersion(2L);
            }
            var e=entry("1","CONTRACT","备注");assertThrows(IllegalArgumentException.class,()->owner.apply(batch(e),List.of(e)),fault);
        }
        verify(mapper,never()).updateFields(any());
    }
    @Test void changedSourceProofOrBindingDoesNotReachAnyWrite() {
        var e=entry("1","CONTRACT","备注");var changed=new DppmsOrderFieldCarryOwner.Entry(e.source(),e.fact(),e.proof(),"e".repeat(64),e.originalFields());
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(changed),List.of(changed)));
        var rebound=new DppmsOrderFieldCarryOwner.Entry(new Row("ORDER","1",Map.of(),21L),e.fact(),e.proof(),sourceHash,e.originalFields());
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(rebound),List.of(rebound)));verifyNoInteractions(mapper);
    }
    @Test void twoCurrentCompanyAliasesBlockEvenWithAReviewedTargetBinding() {
        var duplicate=current();duplicate.setId(21L);duplicate.setCompanyCode("001");duplicate.setSourceKey("DPPMS|D365|001|0|ORDER");
        when(identities.selectOrders(any())).thenReturn(List.of(current,duplicate));var e=entry("1","CONTRACT","备注");
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(e),List.of(e)));verify(mapper,never()).updateFields(any());
    }
    @Test void conflictingAliasesAreAllValidatedBeforeTheFirstWrite() {
        var a=entry("1","CONTRACT","备注");var b=entry("2","DIFFERENT","备注");
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(a,b),List.of(a,b)));verify(mapper,never()).updateFields(any());
    }
    @Test void ownerAndReplacementGuardsStayEffective() {
        var e=entry("1","CONTRACT","备注");
        for(var bad:List.of(new Batch("manual",List.of(e.source()),List.of(),true,"RETAIN",false,"UPSERT",false),
                new Batch("integration:2:100",List.of(e.source()),List.of(),true,"RETAIN",false,"UPSERT",false),
                new Batch("integration:1:100",List.of(e.source()),List.of(),true,"RETAIN",true,"UPSERT",false),
                new Batch("integration:1:100",List.of(e.source()),List.of(),true,"RETAIN",false,"UPSERT",true)))
            assertThrows(IllegalArgumentException.class,()->owner.apply(bad,List.of(e)));
        verifyNoInteractions(mapper);
    }
    @Test void aLostRowVersionUpdateIsAFailureRatherThanSuccess() {
        when(mapper.updateFields(any())).thenReturn(0);var e=entry("1","CONTRACT","备注");
        assertThrows(IllegalStateException.class,()->owner.apply(batch(e),List.of(e)));
    }
    @Test void aChangedEditableMappingCannotWriteDifferentFieldsBehindAnUnchangedRawFingerprint() {
        var e=entry("1","CONTRACT","备注");var raw=new LinkedHashMap<>(e.originalFields());raw.put("contractNo","OTHER-SOURCE-CONTRACT");
        var changed=new DppmsOrderFieldCarryOwner.Entry(e.source(),e.fact(),e.proof(),sourceHash,raw);
        assertThrows(IllegalArgumentException.class,()->owner.apply(batch(changed),List.of(changed)));verifyNoInteractions(mapper);
    }
}
