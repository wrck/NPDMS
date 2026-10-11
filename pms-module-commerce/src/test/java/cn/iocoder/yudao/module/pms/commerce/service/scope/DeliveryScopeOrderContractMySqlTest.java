package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderContractQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderContractRow;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-private tables exercise the production XML without changing real commerce facts. */
@EnabledIfSystemProperty(named="skipITs",matches="false")
class DeliveryScopeOrderContractMySqlTest {
    private java.sql.Connection connection;
    private SqlSession session;
    private DeliveryScopeMapper mapper;
    @BeforeEach void fixtures() throws Exception {
        assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));
        connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        try(var sql=connection.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE com_delivery_scope(id BIGINT PRIMARY KEY, tenant_id BIGINT, order_line_id BIGINT, deleted BIT)");
            sql.execute("CREATE TEMPORARY TABLE com_sales_order_line(id BIGINT PRIMARY KEY, tenant_id BIGINT, order_id BIGINT, deleted BIT)");
            sql.execute("CREATE TEMPORARY TABLE com_sales_order(id BIGINT PRIMARY KEY, tenant_id BIGINT, contract_no VARCHAR(64), deleted BIT)");
            sql.execute("INSERT INTO com_sales_order VALUES (1,1,'C1',b'0'),(2,1,'C2',b'0'),(3,2,'FOREIGN',b'0'),(4,1,'DELETED',b'1'),(5,1,NULL,b'0'),(6,1,'OUTSIDE',b'0')");
            sql.execute("INSERT INTO com_sales_order_line VALUES (11,1,1,b'0'),(12,1,2,b'0'),(13,1,3,b'0'),(14,2,3,b'0'),(15,1,1,b'1'),(16,1,4,b'0'),(17,1,999,b'0'),(18,1,5,b'0'),(19,1,1,b'0'),(20,1,6,b'0')");
            sql.execute("INSERT INTO com_delivery_scope VALUES (101,1,11,b'0'),(102,1,12,b'0'),(103,1,13,b'0'),(104,2,14,b'0'),(105,1,15,b'0'),(106,1,16,b'0'),(107,1,17,b'0'),(108,1,18,b'0'),(109,1,19,b'1'),(110,1,20,b'0')");
        }
        var configuration=new Configuration(new Environment("scope-contract",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
        try(var xml=getClass().getResourceAsStream("/mapper/scope/DeliveryScopeMapper.xml")) {
            new XMLMapperBuilder(xml,configuration,"scope-contract",configuration.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper=session.getMapper(DeliveryScopeMapper.class);
    }
    @AfterEach void close() throws Exception { if(session!=null)session.close(); if(connection!=null)connection.close(); }
    @Test void eachScopeUsesItsOwnOrderContractAndDoesNotReadOutsideTheAuthorizedPage() {
        assertEquals(List.of(new DeliveryScopeOrderContractRow(101L,"C1"),new DeliveryScopeOrderContractRow(102L,"C2")),mapper.selectOrderContracts(new DeliveryScopeOrderContractQuery(1L,List.of(101L,102L))));
    }
    @Test void tenantAndLogicalDeletionApplyToEveryJoinedOwnerTable() {
        assertEquals(List.of(),mapper.selectOrderContracts(new DeliveryScopeOrderContractQuery(1L,List.of(103L,104L,105L,106L,107L,109L))));
        assertEquals(List.of(new DeliveryScopeOrderContractRow(104L,"FOREIGN")),mapper.selectOrderContracts(new DeliveryScopeOrderContractQuery(2L,List.of(104L))));
    }
    @Test void missingAndEmptyAuthorizedScopeDoNotExpandToAllOrders() {
        assertEquals(List.of(),mapper.selectOrderContracts(new DeliveryScopeOrderContractQuery(1L,null)));
        assertEquals(List.of(),mapper.selectOrderContracts(new DeliveryScopeOrderContractQuery(1L,List.of())));
    }
    @Test void missingContractNumberRemainsUnknown() {
        assertEquals(List.of(new DeliveryScopeOrderContractRow(108L,null)),mapper.selectOrderContracts(new DeliveryScopeOrderContractQuery(1L,List.of(108L))));
    }
}
