package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask;
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

@EnabledIfEnvironmentVariable(named="BASE_ORGANIZATION_READ_ONLY",matches="true")
class OrganizationBackfillReadOnlyMySqlTest {
    @Test void actualLocalCompletionRowsMapToTheProtectedJavaFingerprintWithoutAnyWrite()throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(var connection=DriverManager.getConnection("jdbc:mysql://172.17.90.178:13306/npdms?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("BASE_ORGANIZATION_USER"),System.getenv("BASE_ORGANIZATION_PASSWORD"))){
            try(var statement=connection.createStatement()){statement.execute("SET SESSION transaction_read_only=1");}
            var configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment("read-only",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            var resource="mapper/basesync/P15TargetMapper.xml";
            try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
            try(var session=new SqlSessionFactoryBuilder().build(configuration).openSession()){
                var mapper=session.getMapper(P15DeliverySyncTask.TargetMapper.class);
                var query=new AbstractSyncTask.SourceQuery<>(200000,1,true,new P15DeliverySyncTask.Parameters(Map.of(),2112881203220676609L));
                var samples=new ArrayList<P15DeliverySyncTask.Source>();long[] counts={0,0};
                mapper.selectShipmentContractOrganization(query,result->{
                    var source=result.getResultObject();assertNotNull(source.getKey());assertNotNull(source.getTarget());assertNotNull(source.getTarget().getId());
                    assertEquals(1L,source.getTarget().getTenantId());counts[0]++;
                    if(source.getResolvedContractId()!=null){assertNotNull(source.getResolvedCompanyCode());counts[1]++;if(samples.size()<3)samples.add(source);}
                });
                assertEquals(120638,counts[0]);assertEquals(77709,counts[1]);
                for(var source:samples)assertBinding(connection,"SHIPMENT_CONTRACT",source);
                samples.clear();mapper.selectContractOrganization(new AbstractSyncTask.SourceQuery<>(3,1,true,query.parameters()),result->samples.add(result.getResultObject()));
                assertEquals(3,samples.size());for(var source:samples)assertBinding(connection,"CONTRACT",source);
            }
        }
    }
    static void assertBinding(Connection connection,String object,P15DeliverySyncTask.Source source)throws Exception{
        try(var statement=connection.prepareStatement("SELECT fingerprint FROM int_base_sync_binding WHERE tenant_id=1 AND task_id=2112881208044126209 AND object_key=? AND target_id=? AND deleted=FALSE")){
            statement.setString(1,object);statement.setLong(2,source.getTarget().getId());
            try(var result=statement.executeQuery()){
                assertTrue(result.next());String kind=object.equals("CONTRACT")?"Contract":"ShipmentContract";
                assertEquals(result.getString(1),P15DeliverySyncTask.fingerprint(new P15DeliverySyncTask.Validation(kind,source.getTarget())));
                assertFalse(result.next());
            }
        }
    }
}
