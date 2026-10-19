package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectOrganizationTransactionTest {
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    DirectOrganizationMapper mapper;
    SyncBindingMapper bindingMapper;
    TransactionTemplate transaction;
    DppmsDirectOrganizationTask task;
    @BeforeEach void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:p01-direct-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc = new JdbcTemplate(source);
        String shared = "id BIGINT PRIMARY KEY,code VARCHAR(64),name VARCHAR(128),status INT,version INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE, UNIQUE(tenant_id,code,deleted)";
        jdbc.execute("CREATE TABLE system_company(" + shared + ")");
        jdbc.execute("CREATE TABLE system_dept(parent_id BIGINT,sort INT,leader_user_id BIGINT,phone VARCHAR(32),email VARCHAR(64)," + shared + ")");
        jdbc.execute("CREATE TABLE system_users(id BIGINT,tenant_id BIGINT,deleted BOOLEAN)");
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        try (var input = new ClassPathResource("mapper/sync/DirectOrganizationMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,configuration,"mapper/sync/DirectOrganizationMapper.xml",configuration.getSqlFragments()).parse();
        }
        try (var input = new ClassPathResource("mapper/sync/SyncBindingMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,configuration,"mapper/sync/SyncBindingMapper.xml",configuration.getSqlFragments()).parse();
        }
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        mapper = session.getMapper(DirectOrganizationMapper.class);
        bindingMapper = session.getMapper(SyncBindingMapper.class);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        task = new DppmsDirectOrganizationTask(mapper,null,null,null,source,null);
    }
    DppmsDirectOrganizationTask.Execution execution(boolean apply) {
        return new DppmsDirectOrganizationTask.Execution(DirectOrganizationPlanTest.batch(DirectOrganizationPlanTest.rows(),List.of()),apply,1,9L,"9",DirectOrganizationPlanTest.NOW);
    }
    @Test void previewNeverWritesAndApplyRequiresAnActualTransaction() {
        assertEquals(12,task.syncData(execution(false)).size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertThrows(IllegalStateException.class,() -> task.syncData(execution(true)));
    }
    @Test void receiptFailureRollsBackBothTablesAndSuccessfulTransactionPersistsParentReferences() {
        assertThrows(IllegalStateException.class,() -> transaction.execute(status -> {
            task.syncData(execution(true)); throw new IllegalStateException("模拟绑定回执写入失败");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
        transaction.executeWithoutResult(status -> task.syncData(execution(true)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
        assertEquals(9,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept d JOIN system_dept p ON d.parent_id=p.id WHERE p.code='160000'",Integer.class));
    }
    @Test void catalogueAndLeaderQueriesKeepTenantDeletedAndEmptySetBoundaries() {
        jdbc.update("INSERT INTO system_company(id,code,name,tenant_id,deleted) VALUES(1,'001','其他租户',2,FALSE),(2,'002','已删除',1,TRUE)");
        assertTrue(mapper.selectCompanies(new Scope(1)).isEmpty());
        jdbc.update("INSERT INTO system_users VALUES(1,1,FALSE),(2,2,FALSE),(3,1,TRUE)");
        assertEquals(List.of(1L),mapper.selectLeaders(new Leaders(1,Set.of(1L,2L,3L))));
        assertTrue(mapper.selectLeaders(new Leaders(1,Set.of())).isEmpty());
    }
    @Test void downstreamAliasesIncludeNewTaskButExcludeOtherTenantConnectionAndEmptyKeys() {
        jdbc.execute("CREATE TABLE int_sync_task(id BIGINT PRIMARY KEY,tenant_id BIGINT,source_system VARCHAR(30),adapter VARCHAR(64),connection_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE int_sync_binding(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,object_key VARCHAR(64),source_object VARCHAR(64),source_key VARCHAR(128),target_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.update("INSERT INTO int_sync_task(id,tenant_id,source_system,adapter,connection_id) VALUES(1,1,'DPPMS','DPPMS_ORGANIZATION_DIRECT',9),(2,2,'DPPMS','DPPMS_ORGANIZATION_DIRECT',9),(3,1,'DPPMS','DPPMS_ORGANIZATION_DIRECT',10)");
        jdbc.update("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id) VALUES(1,1,1,'COMPANY_REFERENCE','t_company','90',100),(2,2,2,'COMPANY_REFERENCE','t_company','90',200),(3,1,3,'COMPANY_REFERENCE','t_company','90',300)");
        assertEquals(List.of(100L),bindingMapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_ORGANIZATION","t_company",List.of("90"))).stream().map(b -> b.getTargetId()).toList());
        assertTrue(bindingMapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_ORGANIZATION","t_company",List.of())).isEmpty());
    }
    @Test void leaderUpdateCannotCrossTenantOrOverwriteExistingLeader() {
        jdbc.update("INSERT INTO system_dept(id,code,name,tenant_id,status,version,leader_user_id) VALUES(1,'160000','部门',1,0,0,NULL)");
        Node before = mapper.selectDepartments(new Scope(1)).getFirst();
        Node after = new Node(1L,"DEPARTMENT","160000","部门",0L,0,55L,null,null,0,1,null,null,"9",DirectOrganizationPlanTest.NOW);
        assertEquals(0,mapper.completeLeader(new LeaderWrite(2,before,after)));
        assertEquals(1,mapper.completeLeader(new LeaderWrite(1,before,after)));
        assertEquals(0,mapper.completeLeader(new LeaderWrite(1,before,after)));
    }
}
