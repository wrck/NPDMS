package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.ContractReceivableDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractReceivableMigrationServiceTest {
    ContractReceivableMigrationMapper mapper=mock(ContractReceivableMigrationMapper.class);
    AdminUserApi users=mock(AdminUserApi.class);PermissionApi permissions=mock(PermissionApi.class);
    ContractReceivableMigrationService service=new ContractReceivableMigrationService(mapper,users,permissions);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);when(permissions.hasAnyPermissions(eq(7L),any())).thenReturn(true);
        when(mapper.selectSourceForUpdate(any())).thenReturn(List.of());when(mapper.selectExactContractIds(any())).thenReturn(List.of());}
    @AfterEach void close(){TenantContextHolder.clear();}
    static Map<String,String> source() {
        var values=new LinkedHashMap<String,String>();ContractReceivableMigrationApi.ORIGINAL_COLUMNS.forEach(name->values.put(name,null));
        values.put("id","11");values.put("contract_num","C-01");values.put("dataSource","CRM");values.put("create_by","legacy");
        values.put("update_by","crmapp");values.put("create_time","2023-06-01T12:01:02.123");values.put("update_time","2023-07-02T09:08:07.654");
        values.put("contract_money_amount","123.45");values.put("delivered_money_amount","112.34");values.put("collected_money_amount","101.23");
        values.put("collected_money_ratio","81.999999");values.put("receivables_money_amount","22.22");values.put("over_due_money_amount","3.45");
        values.put("systemid_o","4");values.put("industry_name_o","旧行业");
        return values;
    }
    static Entry entry(Map<String,String> source,Long id,String baseline){return new Entry("11",source,"91","crmapp",id,baseline);}
    static Request request(Entry entry){return new Request(1L,7L,"integration:1:9",List.of(entry));}
    @Test void preservesAllFinancialValuesAndOriginalAuditsWithoutMirrors() {
        when(mapper.insertReceipt(any())).thenReturn(1);
        var result=service.apply(request(entry(source(),null,null))).getFirst();
        assertEquals("CREATED",result.action());assertNotNull(result.targetId());
        assertEquals("81.999999",result.fields().get("collectedRatio"));
        assertEquals("2023-06-01T12:01:02.123",result.fields().get("createTime"));
        assertEquals("crmapp",result.fields().get("updater"));assertEquals(52,result.fields().size());
        assertTrue(result.fields().get("originalDivisionValues").toString().contains("\"systemSourceKey\":4"));
        assertFalse(result.fields().containsKey("sourceRowId"));assertFalse(result.fields().containsKey("sourceCollectedMoneyRatio"));
    }
    @Test void filtersOnlyNullContractNumberAsExplicitlyDecided() {
        var f=source();f.put("contract_num",null);
        assertEquals("SKIPPED",service.apply(request(entry(f,null,null))).getFirst().action());
        verify(mapper,never()).selectSourceForUpdate(any());verify(mapper,never()).insertReceipt(any());
    }
    @Test void refusesRoundingFinancialInformation() {
        var f=source();f.put("collected_money_ratio","1.0000001");
        assertThrows(ArithmeticException.class,()->service.preview(request(entry(f,null,null))));
        verify(mapper,never()).insertReceipt(any());
    }
    @Test void refusesUnapprovedExistingRowAndSubsequentManualChanges() {
        var old=ContractReceivableMigrationService.entity(entry(source(),null,null),1L);old.setId(17L);old.setMappingStatus("PENDING_CONTRACT");
        String baseline=ContractReceivableMigrationService.fingerprint(old);
        when(mapper.selectSourceForUpdate(any())).thenReturn(List.of(old));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(entry(source(),null,null))));
        old.setCollectedAmount(new BigDecimal("999"));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(entry(source(),17L,baseline))));
        verify(mapper,never()).updateReceipt(any());
    }
    @Test void synchronizesSourceChangesOnlyWithUnchangedCurrentBaseline() {
        var old=ContractReceivableMigrationService.entity(entry(source(),null,null),1L);old.setId(17L);old.setMappingStatus("MATCHED");
        old.setContractId(21L);old.setCompanyCode("001");String baseline=ContractReceivableMigrationService.fingerprint(old);
        when(mapper.selectSourceForUpdate(any())).thenReturn(List.of(old));when(mapper.updateReceipt(any())).thenReturn(1);
        var f=source();f.put("collected_money_amount","102.23");f.put("update_time","2023-08-01T01:02:03.456");
        var result=service.apply(request(entry(f,17L,baseline))).getFirst();
        assertEquals("UPDATED",result.action());assertEquals(17L,result.targetId());assertEquals("102.23",result.fields().get("collectedAmount"));
        assertEquals("001",result.fields().get("companyCode"));assertEquals(21L,result.fields().get("contractId"));
    }
    @Test void rejectsTenantAndUnauthorizedActor() {
        assertThrows(IllegalArgumentException.class,()->service.apply(new Request(2L,7L,"integration:2:9",List.of())));
        when(permissions.hasAnyPermissions(eq(7L),any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(source(),null,null))));
        verifyNoInteractions(mapper);
    }
}
