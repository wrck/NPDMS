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
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BASE_ORGANIZATION_READ_ONLY",matches="true")
class OrderContractReadOnlyMySqlTest {
    @Test void existingBoundOrderResolvesOnlySameConnectionTenantAndExactCompanyContract() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:mysql://172.17.90.178:13306/npdms?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("BASE_ORGANIZATION_USER"),System.getenv("BASE_ORGANIZATION_PASSWORD"))){
            long connectionId;
            try(var statement=connection.createStatement()){
                statement.execute("SET SESSION transaction_read_only=1");
                try(var result=statement.executeQuery("SELECT connection_id FROM int_base_sync_task WHERE tenant_id=1 AND task_code='BASE_P14' AND deleted=0")){
                    assertTrue(result.next());connectionId=result.getLong(1);assertFalse(result.next());
                }
            }
            var configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);configuration.setDefaultStatementTimeout(30);
            configuration.setEnvironment(new Environment("read-only",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            String resource="mapper/basesync/P15TargetMapper.xml";
            try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
            try(var session=new SqlSessionFactoryBuilder().build(configuration).openSession()){
                var mapper=session.getMapper(P15DeliverySyncTask.TargetMapper.class);
                var sample=new ArrayList<P15DeliverySyncTask.Source>();long[] counts={0,0};
                var parameters=new P15DeliverySyncTask.Parameters(Map.of(),connectionId);
                mapper.selectOrderContractRelation(new AbstractSyncTask.SourceQuery<>(100000,1,true,parameters),context->{
                    var row=context.getResultObject();counts[0]++;if(row.getRelation().getContractId()==null)counts[1]++;
                    if(row.getRelation().getSalesOrderSourceKey().equals("BASE_P14|ORDER|D365|001|0|SO064298"))sample.add(row);
                });
                assertEquals(92890,counts[0]);assertEquals(7,counts[1]);assertEquals(1,sample.size());
                var row=sample.getFirst().getRelation();assertEquals(2113086178878129525L,row.getOrderId());
                assertEquals(2113080482572795998L,row.getContractId());assertEquals("D365",row.getSourceSystem());
                assertEquals(LocalDateTime.of(2023,2,24,11,19,34),row.getEffectiveFrom());
                mapper.selectOrderContractRelation(new AbstractSyncTask.SourceQuery<>(10,2,true,parameters),context->fail("cross-tenant order leaked"));
                mapper.selectOrderContractRelation(new AbstractSyncTask.SourceQuery<>(10,1,true,new P15DeliverySyncTask.Parameters(Map.of(),-1)),context->fail("cross-connection order leaked"));
                assertTrue(mapper.selectOrderContractRelations(new P15DeliverySyncTask.OrderContractQuery(1,List.of(),true)).isEmpty());
            }
        }
    }
}
