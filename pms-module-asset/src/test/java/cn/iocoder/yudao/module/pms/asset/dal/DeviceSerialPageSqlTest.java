package cn.iocoder.yudao.module.pms.asset.dal;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeviceSerialPageSqlTest {
    @Test void returnsDeviceLogVersionsAndFullRenewalCountWithoutOtherTenantOrDeletedFacts() throws Exception {
        var config=new Configuration();
        for (String resource : List.of("DeviceCurrentModelMapper", "DeviceQueryMapper")) {
            try (var xml = getClass().getResourceAsStream("/mapper/device/" + resource + ".xml")) {
                new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
            }
        }
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:serial_page;MODE=MySQL")){
            var fields=new ArrayList<String>();
            for(var field:DeviceListProjection.class.getRecordComponents()){
                if(Set.of("bootVersion","cpldVersion","extendedCount").contains(field.getName()))continue;
                String name=field.getName().equals("deviceId")?"id":field.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();
                fields.add(name+(field.getType()==Long.class?" BIGINT":" VARCHAR(255)"));
            }
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE ast_device("+String.join(",",fields)+",tenant_id BIGINT,deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(100),project_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_product_official_info(tenant_id BIGINT,product_code VARCHAR(64),product_model VARCHAR(64),product_name VARCHAR(64),product_desc VARCHAR(255),deleted BOOLEAN,UNIQUE(tenant_id,product_code))");
            sql.execute("CREATE TABLE ast_device_network_version(tenant_id BIGINT,device_sn VARCHAR(100),source_system VARCHAR(20),sync_status VARCHAR(20),boot_version VARCHAR(20),cpld_version VARCHAR(20),deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_device_warranty_record(tenant_id BIGINT,device_sn VARCHAR(100),extended BOOLEAN,deleted BOOLEAN)");
            sql.execute("INSERT INTO ast_device(id,sn,project_id,tenant_id,deleted) VALUES(1,'SN1',10,1,false),(2,'SN2',10,1,false)");
            sql.execute("UPDATE ast_device SET warranty_start_date='2026-01-01',warranty_end_date='2027-01-01',warranty_status='ACTIVE' WHERE id=2");
            sql.execute("INSERT INTO ast_device(id,sn,project_id,tenant_id,deleted) VALUES(3,'OTHER_PROJECT',20,1,false),(4,'OTHER_TENANT',10,2,false),(5,'DELETED',10,1,true)");
            sql.execute("UPDATE ast_device SET company_id=11,company_name='Stored Company',department_id=22,department_code='OFFICE',department_name='Stored Office',organization_source='CONTRACT' WHERE id=2");
            sql.execute("INSERT INTO ast_device_network_version VALUES(1,'SN1','ITR','FRESH','ITRBOOT','ITRCPLD',false),(2,'SN1','DEVICE_LOG','FRESH','OTHER','OTHER',false),(1,'SN2','DEVICE_LOG','FRESH','BOOT','CPLD',false)");
            sql.execute("INSERT INTO ast_device_warranty_record VALUES(1,'SN2',true,false),(1,'SN2',true,false),(1,'SN2',false,false),(1,'SN2',true,true),(2,'SN2',true,false)");
            var params=Map.of("query",VisibleDevicePageQuery.builder()
                .tenantId(1L)
                .visibleProjectIds(Set.of(10L))
                .projectId(10L)
                .pageNo(1)
                .pageSize(40)
                .visibleContractNumbers(Set.of())
                .organizationGrants(List.of()).build());
            var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper.selectVisibleDeviceList").getBoundSql(params);
            try(var prepared=connection.prepareStatement(bound.getSql().replace("b'0'","FALSE").replace("b'1'","TRUE"))){
                int index=1;for(var mapping:bound.getParameterMappings()){var name=mapping.getProperty();prepared.setObject(index++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(params).getValue(name));}
                try(var rows=prepared.executeQuery()){
                    assertTrue(rows.next());assertEquals("SN2",rows.getString("sn"));assertEquals("BOOT",rows.getString("boot_version"));assertEquals("CPLD",rows.getString("cpld_version"));assertEquals("2026-01-01",rows.getString("warranty_start_date"));assertEquals("2027-01-01",rows.getString("warranty_end_date"));assertEquals("ACTIVE",rows.getString("warranty_status"));assertEquals(2,rows.getLong("extended_count"));
                    assertEquals(11L,rows.getLong("company_id"));assertEquals("Stored Company",rows.getString("company_name"));
                    assertEquals(22L,rows.getLong("department_id"));assertEquals("OFFICE",rows.getString("department_code"));
                    assertEquals("Stored Office",rows.getString("department_name"));assertEquals("CONTRACT",rows.getString("organization_source"));
                    assertTrue(rows.next());assertEquals("SN1",rows.getString("sn"));assertNull(rows.getString("boot_version"));assertNull(rows.getObject("department_id"));assertNull(rows.getString("organization_source"));assertEquals(0,rows.getLong("extended_count"));assertFalse(rows.next());
                }
            }
            var base=(VisibleDevicePageQuery)params.get("query");
            assertEquals(List.of("SN2:2"), page(connection, config, base.toBuilder().pageSize(1).build()));
            assertEquals(List.of("SN1:0"), page(connection, config, base.toBuilder().pageSize(1).pageNo(2).build()));
            assertTrue(page(connection, config, base.toBuilder().pageSize(1).pageNo(3).build()).isEmpty());
            assertTrue(page(connection, config, base.toBuilder().visibleProjectIds(Set.of()).build()).isEmpty());
            assertTrue(page(connection, config, base.toBuilder().matchingCustomerIds(Set.of()).build()).isEmpty());
            assertTrue(page(connection, config, base.toBuilder().sn("SN1").matchingProjectIds(Set.of(20L)).build()).isEmpty());
            assertEquals(List.of("SN1:0"), page(connection, config, base.toBuilder().sn("SN1").build()));
        }
    }

    private List<String> page(Connection connection, Configuration config, VisibleDevicePageQuery query) throws Exception {
        var params=Map.of("query",query);
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper.selectVisibleDeviceList").getBoundSql(params);
        try(var prepared=connection.prepareStatement(bound.getSql().replace("b'0'","FALSE").replace("b'1'","TRUE"))){
            int index=1;
            for(var mapping:bound.getParameterMappings()){
                var name=mapping.getProperty();
                prepared.setObject(index++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(params).getValue(name));
            }
            var result=new ArrayList<String>();
            try(var rows=prepared.executeQuery()){
                while(rows.next())result.add(rows.getString("sn")+":"+rows.getLong("extended_count"));
            }
            return result;
        }
    }
}
