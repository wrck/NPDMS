package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BASE_ORGANIZATION_ROLLBACK",matches="true")
class OrganizationDeviceRollbackMySqlTest {
    @Test void exactNativeDeviceUpdatePreservesBusinessAuditAndFingerprintAndRollsBack()throws Exception{
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(var connection=DriverManager.getConnection("jdbc:mysql://172.17.90.178:13306/npdms?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("BASE_ORGANIZATION_USER"),System.getenv("BASE_ORGANIZATION_PASSWORD"))){
            connection.setAutoCommit(false);
            String sn;String fingerprint;
            try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT d.sn,b.fingerprint FROM int_base_sync_binding b FORCE INDEX(uk_int_base_sync_binding_source) STRAIGHT_JOIN ast_device d ON d.tenant_id=b.tenant_id AND d.id=b.target_id WHERE b.tenant_id=1 AND b.task_id=2112881209449218050 AND b.object_key='DEVICE_BASE' AND b.deleted=FALSE AND d.deleted=FALSE AND d.project_id IS NULL AND d.source_system='DPPMS' AND d.company_id IS NOT NULL ORDER BY b.source_key LIMIT 1")){
                assertTrue(result.next());sn=result.getString(1);fingerprint=result.getString(2);
            }
            var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("owned-device-rollback",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            var resource="mapper/basesync/P30TargetMapper.xml";try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
            try(var session=new SqlSessionFactoryBuilder().build(config).openSession()){
                var mapper=session.getMapper(P30DeviceBaseSyncTask.TargetMapper.class);var query=new P30DeviceBaseSyncTask.TargetMapper.SerialsQuery(1,List.of(sn),false);
                var before=mapper.selectExisting(query).getFirst();assertEquals(fingerprint,P30DeviceBaseSyncTask.fingerprint(before));
                var target=new P30DeviceBaseSyncTask.DeviceRow();org.springframework.beans.BeanUtils.copyProperties(before,target);
                var row=new P30DeviceBaseSyncTask.DeviceSource().setTarget(target);
                var context=OrganizationBackfillSqlTest.context().forParameters(new P30DeviceBaseSyncTask.Parameters());
                assertEquals(1,mapper.updateDeviceOrganization(new SyncBatch<>(context,List.of(row))));
                var after=mapper.selectExisting(query).getFirst();assertEquals(before.getUpdateTime(),after.getUpdateTime());
                assertEquals(fingerprint,P30DeviceBaseSyncTask.fingerprint(after));assertEquals(context.startedAt(),after.getOrganizationUpdatedAt());
                session.rollback(true);
                var restored=mapper.selectExisting(query).getFirst();assertEquals(before.getOrganizationUpdatedAt(),restored.getOrganizationUpdatedAt());
                assertEquals(fingerprint,P30DeviceBaseSyncTask.fingerprint(restored));
            }finally{if(!connection.getAutoCommit())connection.rollback();}
        }
    }
}
