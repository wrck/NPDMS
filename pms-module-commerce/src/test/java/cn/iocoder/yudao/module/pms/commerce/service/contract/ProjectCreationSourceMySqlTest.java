package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractIdLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ContractCreationOrderQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionNoListQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionPrimaryProjectUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Real production XML against the explicitly authorized local test DB. No deletes, grants or mocks.
 * Fixtures have a dedicated source prefix; all mutation checks roll back their own transactions.
 * This verifies mapper SQL/locking, not the HTTP application or its authorization wiring.
 */
@EnabledIfSystemProperty(named="projectCreation.mysql", matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectCreationSourceMySqlTest {
    private SqlSessionFactory factory;
    private long contractId;
    private long otherContractId;
    private long relationId;
    private final String prefix="PCREATE-20261006-710A-" + UUID.randomUUID().toString().substring(0,8);
    private String contractNo;

    @BeforeAll void initialize() throws Exception {
        assertEquals("npdms_domain_test", System.getenv("NPDMS_DB_NAME"), "Only the explicitly authorized test schema is allowed");
        assertEquals("24306", System.getenv("NPDMS_MYSQL_PORT"), "Only the named local Compose endpoint is allowed");
        var dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                required("NPDMS_DB_USER"),required("NPDMS_DB_PASSWORD"));
        var configuration=new Configuration(new Environment("creation-source-mysql",new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        for(String resource:List.of("mapper/contract/ContractMapper.xml","mapper/order/SalesOrderMapper.xml","mapper/executionorder/CrmExecutionOrderMapper.xml")) {
            try(var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        factory=new SqlSessionFactoryBuilder().build(configuration);
        contractNo=prefix+"-CT";
        try(var session=factory.openSession(false)) {
            Connection connection=session.getConnection();
            execute(connection,"INSERT INTO com_contract (tenant_id,company_code,contract_no,master_source_system,master_source_record_key,status,creator,updater) VALUES (1,'DPTECH-DEMO',?,'SYNTHETIC',?,'ENABLED',?,?)",contractNo,prefix,prefix,prefix);
            try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT LAST_INSERT_ID()")){assertTrue(rs.next());contractId=rs.getLong(1);}
            order(connection,"A",1,"DPTECH-DEMO",contractNo,"ENABLED","0");
            order(connection,"B",1,"DPTECH-DEMO",contractNo,"ENABLED","0");
            order(connection,"OTHER-COMPANY",1,"OTHER-SYNTHETIC",contractNo,"ENABLED","0");
            order(connection,"DISABLED",1,"DPTECH-DEMO",contractNo,"DISABLED","0");
            order(connection,"OTHER-TYPE",1,"DPTECH-DEMO",contractNo,"ENABLED","1");
            order(connection,"OTHER-TENANT",2,"DPTECH-DEMO",contractNo,"ENABLED","0");
            order(connection,"OTHER-CONTRACT",1,"DPTECH-DEMO",contractNo+"-OTHER","ENABLED","0");
            for(String label:List.of("A","B")) execute(connection,"INSERT INTO com_crm_execution_order (tenant_id,source_system,company_code,execution_no,project_name,status,creator,updater) VALUES (1,'SYNTHETIC','DPTECH-DEMO',?,?,'ACTIVE',?,?)",prefix+"-EX-"+label,prefix+"-PROJECT-"+label,prefix,prefix);
            order(connection,"RELATION-ONLY",1,"DPTECH-DEMO",contractNo+"-UNRELATED","ENABLED","0");
            long relatedOrderId;
            try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT LAST_INSERT_ID()")){assertTrue(rs.next());relatedOrderId=rs.getLong(1);}
            relationId=Long.parseUnsignedLong(UUID.randomUUID().toString().replace("-", "").substring(0,15),16);
            execute(connection,"INSERT INTO com_order_contract_relation (id,tenant_id,order_id,contract_id,relation_source,source_system,sales_order_source_key,contract_source_key,source_version,source_evidence,effective_from,creator,updater) VALUES (?,1,?,?,'SYNTHETIC','SYNTHETIC',?,?,'v1','{}',NOW()-INTERVAL 1 DAY,?,?)",
                    relationId,relatedOrderId,contractId,prefix+"-RELATION-ONLY",prefix,prefix,prefix);
            execute(connection,"INSERT INTO com_contract (tenant_id,company_code,contract_no,master_source_system,master_source_record_key,status,creator,updater) VALUES (1,'DPTECH-DEMO',?,'SYNTHETIC',?,'ENABLED',?,?)",contractNo+"-OTHER",prefix+"-OTHER",prefix,prefix);
            try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT LAST_INSERT_ID()")){assertTrue(rs.next());otherContractId=rs.getLong(1);}
            session.commit(true);
        }
        System.out.println("Synthetic fixture prefix="+prefix+", contractId="+contractId+"; retained without deleting existing data");
    }

    @Test void productionQueriesRespectContractCompanyTenantStatusAndOrderType() {
        try(var session=factory.openSession(false)) {
            var orders=session.getMapper(SalesOrderMapper.class).selectCreationOrdersForUpdate(query());
            assertEquals(List.of(prefix+"-SO-A",prefix+"-SO-B",prefix+"-SO-RELATION-ONLY"),orders.stream().map(o->o.getOrderNo()).toList());
            var executions=session.getMapper(CrmExecutionOrderMapper.class).selectActiveForUpdate(executionQuery());
            var selected=CreationSourceResolver.resolve(orders,executions,orders.getFirst().getId());
            assertEquals(prefix+"-EX-A",selected.executionNo());
            assertEquals(prefix+"-PROJECT-A",selected.projectName());
            assertNull(session.getMapper(ContractMapper.class).selectByIdForUpdate(new ContractIdLockQuery(2L,contractId)));
            session.rollback(true);
        }
    }

    @Test void emptyExecutionFilterNeverReturnsGlobalRows() {
        try(var session=factory.openSession(false)) {
            var mapper=session.getMapper(CrmExecutionOrderMapper.class);
            assertTrue(mapper.selectActiveByExecutionNos(new ExecutionNoListQuery(1L,List.of())).isEmpty());
            assertTrue(mapper.selectActiveForUpdate(new ExecutionNoListQuery(1L,List.of())).isEmpty());
            session.rollback(true);
        }
    }

    @Test void contractLockSerializesCompetingCreationTransactions() throws Exception {
        assertBlocks(session->assertNotNull(session.getMapper(ContractMapper.class).selectByIdForUpdate(new ContractIdLockQuery(1L,contractId))),
                session->assertNotNull(session.getMapper(ContractMapper.class).selectByIdForUpdate(new ContractIdLockQuery(1L,contractId))));
    }

    @Test void orderLockPreventsSourceMutationUntilTransactionEnds() throws Exception {
        assertBlocks(session->assertEquals(3,session.getMapper(SalesOrderMapper.class).selectCreationOrdersForUpdate(query()).size()),
                session->execute(session.getConnection(),"UPDATE com_sales_order SET source_version='concurrent-test' WHERE tenant_id=1 AND source_system='SYNTHETIC' AND source_record_key=?",prefix+"-A"));
    }

    @Test void executionLockPreventsSourceMutationUntilTransactionEnds() throws Exception {
        assertBlocks(session->assertEquals(2,session.getMapper(CrmExecutionOrderMapper.class).selectActiveForUpdate(executionQuery()).size()),
                session->execute(session.getConnection(),"UPDATE com_crm_execution_order SET project_name='concurrent-test' WHERE tenant_id=1 AND source_system='SYNTHETIC' AND execution_no=?",prefix+"-EX-A"));
    }

    @Test void conflictingBindingIsRejectedAndRollbackLeavesExecutionUnbound() {
        long executionId;
        try(var session=factory.openSession(false)) {
            var mapper=session.getMapper(CrmExecutionOrderMapper.class);
            var execution=mapper.selectActiveForUpdate(executionQuery()).getFirst(); executionId=execution.getId();
            assertEquals(0,mapper.updatePrimaryProjectIfUnbound(new ExecutionPrimaryProjectUpdate(1L,executionId,994106710001L,1L,"OTHER",null,execution.getExecutionNo(),"SYNTHETIC")));
            assertEquals(0,mapper.updatePrimaryProjectIfUnbound(new ExecutionPrimaryProjectUpdate(1L,executionId,994106710001L,1L,"DPTECH-DEMO",null,execution.getExecutionNo(),"OTHER")));
            assertEquals(1,mapper.updatePrimaryProjectIfUnbound(new ExecutionPrimaryProjectUpdate(1L,executionId,994106710001L,1L,"DPTECH-DEMO",null,execution.getExecutionNo(),"SYNTHETIC")));
            assertEquals(0,mapper.updatePrimaryProjectIfUnbound(new ExecutionPrimaryProjectUpdate(1L,executionId,994106710002L,1L,"DPTECH-DEMO",null,execution.getExecutionNo(),"SYNTHETIC")));
            session.rollback(true);
        }
        try(var verify=factory.openSession(false)) {
            assertTrue(verify.getMapper(CrmExecutionOrderMapper.class).selectActiveByExecutionNos(executionQuery()).stream().allMatch(e->e.getPrimaryProjectId()==null));
            verify.rollback(true);
        }
    }

    @Test void relationOnlyOrderRemainsLockedAgainstExpiryDeletionAndRepointing() throws Exception {
        for(String mutation:List.of("effective_to=NOW()", "deleted=1", "contract_id="+otherContractId)) {
            assertBlocks(session->{
                var mapper=session.getMapper(SalesOrderMapper.class);
                var relations=mapper.selectCreationRelationsForUpdate(new cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ContractRelatedOrderQuery(1L,contractId));
                assertEquals(List.of(relationId),relations.stream().map(r->r.getId()).toList());
                assertTrue(mapper.selectCreationOrdersForUpdate(query()).stream().anyMatch(o->(prefix+"-SO-RELATION-ONLY").equals(o.getOrderNo())));
            },session->execute(session.getConnection(),"UPDATE com_order_contract_relation SET "+mutation+" WHERE tenant_id=1 AND id=? AND creator=?",relationId,prefix));
        }
    }

    @Test void foreignCompanyExecutionIsRejectedWithRealRows() {
        try(var session=factory.openSession(false)) {
            execute(session.getConnection(),"UPDATE com_crm_execution_order SET company_code='OTHER' WHERE tenant_id=1 AND source_system='SYNTHETIC' AND execution_no=?",prefix+"-EX-A");
            var orders=session.getMapper(SalesOrderMapper.class).selectCreationOrdersForUpdate(query());
            var executions=session.getMapper(CrmExecutionOrderMapper.class).selectActiveForUpdate(executionQuery());
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->CreationSourceResolver.resolve(orders,executions,orders.getFirst().getId()));
            session.rollback(true);
        }
    }

    private void assertBlocks(Consumer<SqlSession> ownerOperation,Consumer<SqlSession> competingOperation) throws Exception {
        var started=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor();var owner=factory.openSession(false)) {
            ownerOperation.accept(owner);
            Future<?> competing=executor.submit(()->{
                try(var session=factory.openSession(false)) {
                    execute(session.getConnection(),"SET SESSION innodb_lock_wait_timeout=5");
                    started.countDown(); competingOperation.accept(session); session.rollback(true);
                }
            });
            try {
                assertTrue(started.await(5,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,()->competing.get(300,TimeUnit.MILLISECONDS));
            } finally {owner.rollback(true);}
            competing.get(8,TimeUnit.SECONDS);
        }
    }
    private ContractCreationOrderQuery query(){return new ContractCreationOrderQuery(1L,contractId,contractNo,"DPTECH-DEMO");}
    private ExecutionNoListQuery executionQuery(){return new ExecutionNoListQuery(1L,List.of(prefix+"-EX-A",prefix+"-EX-B"));}
    private void order(Connection c,String label,long tenant,String company,String contract,String status,String type) {
        execute(c,"INSERT INTO com_sales_order (tenant_id,source_system,source_record_key,company_code,order_type,order_no,contract_no,execution_no,status,creator,updater) VALUES (?,'SYNTHETIC',?,?,?,?,?,?,?, ?,?)",
                tenant,prefix+"-"+label,company,type,prefix+"-SO-"+label,contract,prefix+"-EX-"+label,status,prefix,prefix);
    }
    private static void execute(Connection c,String sql,Object... values) {
        try(var statement=c.prepareStatement(sql)) {for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();}
        catch(Exception e){throw new IllegalStateException("Synthetic fixture SQL failed",e);}
    }
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing "+name);return value;}
}
