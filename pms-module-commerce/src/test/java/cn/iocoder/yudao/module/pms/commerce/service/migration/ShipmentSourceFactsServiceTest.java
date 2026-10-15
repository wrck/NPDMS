package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ShipmentSourceFactsMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentSourceFactsServiceTest {
    @Test void deniedAndEmptyScopesDoNotReadAndAmbiguousFactsFailClosed() {
        TenantContextHolder.setTenantId(1L);
        try {
            var mapper=mock(ShipmentSourceFactsMapper.class);var permissions=mock(PermissionApi.class);
            var service=new ShipmentSourceFactsService(mapper,permissions,mock(AdminUserApi.class));
            when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
            assertThrows(IllegalArgumentException.class,()->service.read(new Query(9L,2L,List.of("P1"))));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.read(new Query(1L,3L,List.of("P1"))));
            assertTrue(service.read(new Query(1L,2L,List.of())).isEmpty());verifyNoInteractions(mapper);
            var fact=new Fact("P1",null,"C1");when(mapper.selectFacts(any())).thenReturn(List.of(fact,fact));
            assertThrows(IllegalArgumentException.class,()->service.read(new Query(1L,2L,List.of("P1"))));
        } finally {TenantContextHolder.clear();}
    }
    @Test void actualQueryUsesSourceParentAndTenantAndEmptyScopeIsEmpty() throws Exception {
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:shipmentFacts"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE com_shipment_package(tenant_id BIGINT,deleted BOOLEAN,source_system VARCHAR(20),source_record_key VARCHAR(128),shipment_time TIMESTAMP,shipment_contract_source_key VARCHAR(128))");
        jdbc.execute("CREATE TABLE com_shipment_contract_reference(tenant_id BIGINT,deleted BOOLEAN,source_system VARCHAR(20),source_record_key VARCHAR(128),contract_no VARCHAR(128))");
        jdbc.update("INSERT INTO com_shipment_package VALUES(1,0,'DPPMS','P1','2020-01-01 12:00:00','RAW-1'),(2,0,'DPPMS','P1','2022-01-01 12:00:00','RAW-1')");
        jdbc.update("INSERT INTO com_shipment_contract_reference VALUES(1,0,'DPPMS','RAW-1','C1'),(1,0,'DPPMS','RAW-2','C2'),(2,0,'DPPMS','RAW-1','TENANT-2')");
        var configuration=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
        String resource="mapper/migration/ShipmentSourceFactsMapper.xml";
        try(var in=new org.springframework.core.io.ClassPathResource(resource).getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,configuration,resource,configuration.getSqlFragments()).parse();
        }
        try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var mapper=session.getMapper(ShipmentSourceFactsMapper.class);
            var rows=mapper.selectFacts(new ShipmentSourceFactsMapper.Query(1L,List.of("P1")));
            assertEquals(1,rows.size());assertEquals("C1",rows.getFirst().contractNo());
            assertEquals(2020,rows.getFirst().shipmentTime().getYear());
            assertTrue(mapper.selectFacts(new ShipmentSourceFactsMapper.Query(1L,List.of())).isEmpty());
            assertTrue(mapper.selectFacts(new ShipmentSourceFactsMapper.Query(1L,List.of("MISSING"))).isEmpty());
        }
    }
}
