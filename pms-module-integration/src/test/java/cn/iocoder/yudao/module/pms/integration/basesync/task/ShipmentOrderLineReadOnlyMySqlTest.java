package cn.iocoder.yudao.module.pms.integration.basesync.task;

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
class ShipmentOrderLineReadOnlyMySqlTest {
    @Test void nativeMapperRequiresTheExactBoundTenantCompanySourceTypeAndLine() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:mysql://172.17.90.178:13306/npdms?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("BASE_ORGANIZATION_USER"),System.getenv("BASE_ORGANIZATION_PASSWORD"))){
            try(var statement=connection.createStatement()){statement.execute("SET SESSION transaction_read_only=1");}
            var configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment("read-only",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            var resource="mapper/basesync/P15TargetMapper.xml";
            try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
            var source=new P15DeliverySyncTask.Source().setKey("shipment-check");long expectedId;long connectionId;
            try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT l.id,b.source_key,t.connection_id,l.source_system,l.company_code,l.line_type,l.order_no,l.line_no,l.product_code FROM int_base_sync_task t JOIN int_base_sync_binding b ON b.tenant_id=t.tenant_id AND b.task_id=t.id AND b.object_key='SALES_LINE' AND b.deleted=0 JOIN com_sales_order_line l ON l.tenant_id=b.tenant_id AND l.id=b.target_id AND l.deleted=0 WHERE t.tenant_id=1 AND t.task_code='BASE_P14' AND t.deleted=0 AND l.order_no='SO064298' AND l.line_no='1' AND l.company_code='001' AND l.source_system='D365' AND l.line_type='0'")){
                assertTrue(result.next());expectedId=result.getLong("id");connectionId=result.getLong("connection_id");
                source.setLineSourceKey(result.getString("source_key")).setLineSourceSystem(result.getString("source_system"))
                        .setLineCompanyCode(result.getString("company_code")).setLineType(result.getString("line_type"))
                        .setRawOrderNo(result.getString("order_no")).setFormalLineNo(result.getString("line_no")).setLineProductCode(result.getString("product_code"));
                assertFalse(result.next());
            }
            try(var session=new SqlSessionFactoryBuilder().build(configuration).openSession()){
                var mapper=session.getMapper(P15DeliverySyncTask.TargetMapper.class);
                var query=new P15DeliverySyncTask.ShipmentOrderLineQuery(1,connectionId,List.of(source));
                var matches=mapper.selectShipmentOrderLines(query);assertEquals(1,matches.size());assertEquals(expectedId,matches.getFirst().getOrderLineId());
                assertTrue(mapper.selectShipmentOrderLines(new P15DeliverySyncTask.ShipmentOrderLineQuery(2,connectionId,List.of(source))).isEmpty());
                assertTrue(mapper.selectShipmentOrderLines(new P15DeliverySyncTask.ShipmentOrderLineQuery(1,-1,List.of(source))).isEmpty());
                assertTrue(mapper.selectShipmentOrderLines(new P15DeliverySyncTask.ShipmentOrderLineQuery(1,connectionId,List.of())).isEmpty());
                source.setLineType("1");assertTrue(mapper.selectShipmentOrderLines(query).isEmpty());source.setLineType("0");
                source.setLineCompanyCode("002");assertTrue(mapper.selectShipmentOrderLines(query).isEmpty());source.setLineCompanyCode("001");
                source.setLineSourceSystem("SAP");assertTrue(mapper.selectShipmentOrderLines(query).isEmpty());source.setLineSourceSystem("D365");
                source.setFormalLineNo("10625024");assertTrue(mapper.selectShipmentOrderLines(query).isEmpty());
            }
        }
    }
}
