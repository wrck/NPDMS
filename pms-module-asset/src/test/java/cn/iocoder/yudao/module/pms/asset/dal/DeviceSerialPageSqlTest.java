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
        var config=new Configuration();try(var xml=getClass().getResourceAsStream("/mapper/device/DeviceQueryMapper.xml")){new XMLMapperBuilder(xml,config,"device",config.getSqlFragments()).parse();}
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:serial_page;MODE=MySQL")){
            var fields=new ArrayList<String>();
            for(var field:DeviceListProjection.class.getRecordComponents()){
                if(Set.of("bootVersion","cpldVersion","warrantyMonths","extendedCount").contains(field.getName()))continue;
                String name=field.getName().equals("deviceId")?"id":field.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();
                fields.add(name+(field.getType()==Long.class?" BIGINT":" VARCHAR(255)"));
            }
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE ast_device("+String.join(",",fields)+",tenant_id BIGINT,deleted BOOLEAN,company_id BIGINT,department_id BIGINT,department_code VARCHAR(40),organization_source VARCHAR(20))");
            sql.execute("CREATE TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(100),project_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_device_network_version(tenant_id BIGINT,device_sn VARCHAR(100),source_system VARCHAR(20),sync_status VARCHAR(20),boot_version VARCHAR(20),cpld_version VARCHAR(20),deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_device_warranty(tenant_id BIGINT,device_sn VARCHAR(100),warranty_months INT,deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_device_warranty_record(tenant_id BIGINT,device_sn VARCHAR(100),extended BOOLEAN,deleted BOOLEAN)");
            sql.execute("INSERT INTO ast_device(id,sn,project_id,tenant_id,deleted) VALUES(1,'SN1',10,1,false),(2,'SN2',10,1,false)");
            sql.execute("INSERT INTO ast_device_network_version VALUES(1,'SN1','ITR','FRESH','ITRBOOT','ITRCPLD',false),(2,'SN1','DEVICE_LOG','FRESH','OTHER','OTHER',false),(1,'SN2','DEVICE_LOG','FRESH','BOOT','CPLD',false)");
            sql.execute("INSERT INTO ast_device_warranty VALUES(1,'SN2',12,false),(2,'SN2',99,false)");
            sql.execute("INSERT INTO ast_device_warranty_record VALUES(1,'SN2',true,false),(1,'SN2',true,false),(1,'SN2',false,false),(1,'SN2',true,true),(2,'SN2',true,false)");
            var params=Map.of("query",new VisibleDevicePageQuery(1L,Set.of(10L),null,null,10L,null,1,40,null,null,Set.of(),List.of()));
            var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper.selectVisibleDeviceList").getBoundSql(params);
            try(var prepared=connection.prepareStatement(bound.getSql().replace("b'0'","FALSE").replace("b'1'","TRUE"))){
                int index=1;for(var mapping:bound.getParameterMappings()){var name=mapping.getProperty();prepared.setObject(index++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(params).getValue(name));}
                try(var rows=prepared.executeQuery()){
                    assertTrue(rows.next());assertEquals("SN2",rows.getString("sn"));assertEquals("BOOT",rows.getString("boot_version"));assertEquals("CPLD",rows.getString("cpld_version"));assertEquals(12,rows.getInt("warranty_months"));assertEquals(2,rows.getLong("extended_count"));
                    assertTrue(rows.next());assertEquals("SN1",rows.getString("sn"));assertNull(rows.getString("boot_version"));assertFalse(rows.next());
                }
            }
        }
    }
}
