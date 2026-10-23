package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrganizationAuditTimestampTest {
    @Test void contractBackfillsPreserveAutoUpdatedBusinessAuditTimestamps()throws Exception{
        var original=LocalDateTime.of(2020,1,1,0,0);
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL","sa","")){
            try(var statement=connection.createStatement()){
                statement.execute("CREATE TABLE com_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT,company_id BIGINT,company_name VARCHAR(255),version BIGINT,master_source_system VARCHAR(32),deleted BOOLEAN,update_time TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3))");
                statement.execute("CREATE TABLE com_shipment_contract_reference(id BIGINT PRIMARY KEY,tenant_id BIGINT,contract_id BIGINT,company_code VARCHAR(64),company_id BIGINT,company_name VARCHAR(255),department_id BIGINT,department_name VARCHAR(128),source_system VARCHAR(32),deleted BOOLEAN,update_time TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3))");
                statement.execute("INSERT INTO com_contract(id,tenant_id,version,master_source_system,deleted,update_time) VALUES(1,1,0,'DPPMS_ERP',FALSE,'2020-01-01 00:00:00'),(9,1,0,'DPPMS_ERP',FALSE,'2020-01-01 00:00:00')");
                statement.execute("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,deleted,update_time) VALUES(1,1,'DPPMS',FALSE,'2020-01-01 00:00:00')");
                statement.executeUpdate("UPDATE com_contract SET company_id=100 WHERE id=9");
                try(var result=statement.executeQuery("SELECT update_time FROM com_contract WHERE id=9")){assertTrue(result.next());assertNotEquals(original,result.getObject(1,LocalDateTime.class));}
            }
            var row=new P15DeliverySyncTask.Source().setExpectedVersion(0L).setTarget(new P15DeliverySyncTask.Row().setId(1L)
                    .setCompanyId(100L).setCompanyName("公司").setCompanyCode("001").setContractId(300L).setDepartmentId(200L).setDepartmentName("部门"));
            var context=OrganizationBackfillSqlTest.context().forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
            var batch=new SyncBatch<>(context,List.of(row));var config=OrganizationBackfillSqlTest.config("P15TargetMapper");
            execute(connection,config,P15DeliverySyncTask.TargetMapper.class.getName()+".updateContractOrganization",batch);
            execute(connection,config,P15DeliverySyncTask.TargetMapper.class.getName()+".updateShipmentContractOrganization",batch);
            for(var table:List.of("com_contract","com_shipment_contract_reference"))
                try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT company_id,update_time FROM "+table+" WHERE id=1")){
                    assertTrue(result.next());assertEquals(100L,result.getLong(1));assertEquals(original,result.getObject(2,LocalDateTime.class),table);
                }
        }
    }
    static void execute(Connection connection,Configuration config,String name,Object parameter)throws Exception{
        var bound=config.getMappedStatement(name).getBoundSql(parameter);var meta=config.newMetaObject(parameter);
        try(var statement=connection.prepareStatement(bound.getSql())){
            int index=0;for(var mapping:bound.getParameterMappings()){
                var property=mapping.getProperty();var value=bound.hasAdditionalParameter(property)?bound.getAdditionalParameter(property):meta.getValue(property);
                statement.setObject(++index,value);
            }
            assertEquals(1,statement.executeUpdate());
        }
    }
}
