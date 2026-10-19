package cn.iocoder.yudao.module.pms.asset.service.warranty;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.WarrantyShipmentEligibilityMapper;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WarrantyShipmentEligibilityServiceTest {
    WarrantyShipmentEligibilityMapper mapper=mock(WarrantyShipmentEligibilityMapper.class);
    ShipmentSourceFactsApi commerce=mock(ShipmentSourceFactsApi.class);
    PermissionApi permissions=mock(PermissionApi.class);AdminUserApi users=mock(AdminUserApi.class);
    WarrantyShipmentEligibilityService service=new WarrantyShipmentEligibilityService(mapper,commerce,permissions,users);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);when(permissions.hasAnyPermissions(eq(7L),any())).thenReturn(true);}
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void exactOriginalRmaAndContractTypesDetermineQualificationFromEveryShipment() {
        when(mapper.selectShipments(any())).thenReturn(List.of(
          new WarrantyShipmentEligibilityMapper.Shipment("A","1",null),new WarrantyShipmentEligibilityMapper.Shipment("B","2","null"),
          new WarrantyShipmentEligibilityMapper.Shipment("C","3","RMA"),new WarrantyShipmentEligibilityMapper.Shipment("D","4","")));
        when(commerce.read(any())).thenReturn(List.of(new ShipmentSourceFactsApi.Fact("1",null,"C1","0"),
            new ShipmentSourceFactsApi.Fact("2",null,"C2","11"),new ShipmentSourceFactsApi.Fact("4",null,"C4","1")));
        var result=service.resolve(new Query(1L,7L,List.of("A","B","C","D")));
        assertTrue(result.get(0).eligible());assertTrue(result.get(1).eligible());assertFalse(result.get(2).eligible());assertFalse(result.get(3).eligible());
        assertTrue(result.stream().allMatch(row->row.issue()==null));
        verify(commerce).read(argThat(query->query.packageSourceKeys().equals(List.of("1","2","4"))));
    }
    @Test void missingParentCannotBeHiddenByAnotherEligibleShipment() {
        when(mapper.selectShipments(any())).thenReturn(List.of(new WarrantyShipmentEligibilityMapper.Shipment("A","1",null),new WarrantyShipmentEligibilityMapper.Shipment("A","2",null)));
        when(commerce.read(any())).thenReturn(List.of(new ShipmentSourceFactsApi.Fact("1",null,"C1","0")));
        var result=service.resolve(new Query(1L,7L,List.of("A","B")));
        assertFalse(result.getFirst().eligible());assertEquals("WARRANTY_LOCAL_PARENT_MISSING",result.getFirst().issue());assertEquals("WARRANTY_LOCAL_SHIPMENT_MISSING",result.getLast().issue());
    }
    @Test void unauthorizedTenantAndEmptySelectionNeverExpandScope() {
        assertThrows(IllegalArgumentException.class,()->service.resolve(new Query(2L,7L,List.of("A"))));
        assertTrue(service.resolve(new Query(1L,7L,List.of())).isEmpty());verifyNoInteractions(mapper,commerce);
        when(permissions.hasAnyPermissions(eq(7L),any())).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.resolve(new Query(1L,7L,List.of("A"))));verifyNoInteractions(mapper,commerce);
    }
    @Test void actualShipmentQueryKeepsOriginalRmaNumbersAndNeverExpandsEmptyScope() throws Exception {
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:renewalShipments"+UUID.randomUUID()+";MODE=MySQL");
        try(var keeper=ds.getConnection()) {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE ast_device_shipment(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,source_system VARCHAR(20),device_sn VARCHAR(100),legacy_package_key VARCHAR(128),rma_no VARCHAR(100))");
            jdbc.update("INSERT INTO ast_device_shipment VALUES(1,1,FALSE,'DPPMS','SN','P1','null'),(2,1,FALSE,'DPPMS','SN','P2','RMA'),(3,2,FALSE,'DPPMS','SN','P3',NULL),(4,1,FALSE,'OTHER','SN','P4',NULL),(5,1,TRUE,'DPPMS','SN','P5',NULL)");
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
            String resource="mapper/warranty/WarrantyShipmentEligibilityMapper.xml";
            try(var in=new org.springframework.core.io.ClassPathResource(resource).getInputStream()){new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
            try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()) {
                var actual=session.getMapper(WarrantyShipmentEligibilityMapper.class);
                var rows=actual.selectShipments(new WarrantyShipmentEligibilityMapper.Query(1L,List.of("SN")));
                assertEquals(List.of("P1","P2"),rows.stream().map(WarrantyShipmentEligibilityMapper.Shipment::packageSourceKey).toList());
                assertEquals(List.of("null","RMA"),rows.stream().map(WarrantyShipmentEligibilityMapper.Shipment::rmaNo).toList());
                assertTrue(actual.selectShipments(new WarrantyShipmentEligibilityMapper.Query(1L,List.of())).isEmpty());
            }
        }
    }
}
