package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SyncAccountFoundationBindingTest {
    private SqlSession session;
    private SyncBindingMapper mapper;
    private JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:account-bindings-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE int_sync_task(id BIGINT PRIMARY KEY,tenant_id BIGINT,source_system VARCHAR(30),adapter VARCHAR(64),connection_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE int_sync_binding(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,object_key VARCHAR(64),source_object VARCHAR(64),source_key VARCHAR(128),target_id BIGINT,fields_json VARCHAR(10000),deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.pms.integration.sync.SyncAccountFoundationBindingTest.jsonExtract'");
        jdbc.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.pms.integration.sync.SyncAccountFoundationBindingTest.jsonUnquote'");
        jdbc.update("INSERT INTO int_sync_task(id,tenant_id,source_system,adapter,connection_id) VALUES(1,1,'DPPMS','DPPMS_USERS',9),(2,1,'DPPMS','DPPMS_ACCOUNT_FOUNDATION',9),(3,2,'DPPMS','DPPMS_ACCOUNT_FOUNDATION',9),(4,1,'DPPMS','DPPMS_ACCOUNT_FOUNDATION',10)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));
        try(var input=new ClassPathResource("mapper/sync/SyncBindingMapper.xml").getInputStream()){
            new XMLMapperBuilder(input,configuration,"mapper/sync/SyncBindingMapper.xml",configuration.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(configuration).openSession(true);mapper=session.getMapper(SyncBindingMapper.class);
    }
    @AfterEach void close(){session.close();}
    @Test void directAccountAliasesRemainVisibleToExistingConsumersAndP01WithoutMixingScopes() {
        jdbc.update("INSERT INTO int_sync_task(id,tenant_id,source_system,adapter,connection_id) VALUES(5,1,'DPPMS','DPPMS_ACCOUNT_FOUNDATION_DIRECT',9)");
        bind(new Binding(1,5,1,"fnd_user_info","fnd_user_info","7",101));
        bind(new Binding(2,5,1,"fnd_user_info","fnd_user_info","7:OWN:001:COMPANY:user",301));
        jdbc.update("UPDATE int_sync_binding SET fields_json='{\"_verifiedEhrEmployeeId\":\"293\"}' WHERE id=1");
        var query=new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_USERS","fnd_user_info",List.of("7","7:OWN:001:COMPANY:user"));
        assertEquals(List.of(101L),mapper.selectIdentitySources(query).stream().map(row->row.getTargetId()).toList());
        assertEquals(List.of(101L),mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,9L,Set.of("293"))).stream().map(row->row.getTargetId()).toList());
        assertTrue(mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,10L,Set.of("293"))).isEmpty());
    }
    private record Binding(long id,long task,long tenant,String object,String table,String key,long target) {}
    @Test void directProjectAliasesKeepTenantConnectionObjectAndEmptySetBoundaries() {
        jdbc.update("INSERT INTO int_sync_task(id,tenant_id,source_system,adapter,connection_id) VALUES(5,1,'DPPMS','DPPMS_PROJECT_MASTER_DIRECT',9),(6,2,'DPPMS','DPPMS_PROJECT_MASTER_DIRECT',9),(7,1,'DPPMS','DPPMS_PROJECT_MASTER_DIRECT',10)");
        bind(new Binding(1,5,1,"pm_project_header","pm_project_header","7",101));
        bind(new Binding(2,5,1,"pm_project_identity","pm_project","6",101));
        bind(new Binding(3,6,2,"pm_project_header","pm_project_header","7",901));
        bind(new Binding(4,7,1,"pm_project_header","pm_project_header","7",902));
        bind(new Binding(5,5,1,"unapproved_object","pm_project_header","7",903));
        assertEquals(List.of(101L),mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_PROJECT_MASTER","pm_project_header",List.of("7"))).stream().map(row->row.getTargetId()).toList());
        assertEquals(List.of(101L),mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_PROJECT_MASTER","pm_project",List.of("6"))).stream().map(row->row.getTargetId()).toList());
        assertTrue(mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_PROJECT_MASTER","pm_project_header",List.of())).isEmpty());
    }
    private void bind(Binding row){jdbc.update("INSERT INTO int_sync_binding(id,task_id,tenant_id,object_key,source_object,source_key,target_id) VALUES(?,?,?,?,?,?,?)",
            row.id(),row.task(),row.tenant(),row.object(),row.table(),row.key(),row.target());}
    @Test void mergedAccountAliasesAreVisibleWithoutMixingRelationsOtherTenantsOrConnections(){
        bind(new Binding(1,1,1,"fnd_user_info","fnd_user_info","7",100));
        bind(new Binding(2,2,1,"fnd_user_info","fnd_user_info","7",101));
        bind(new Binding(3,2,1,"FND_USER_ROLE","fnd_user_info","7",201));
        bind(new Binding(4,3,2,"fnd_user_info","fnd_user_info","7",901));
        bind(new Binding(5,4,1,"fnd_user_info","fnd_user_info","7",902));
        bind(new Binding(6,2,1,"fnd_user_info","fnd_user_info","7:OWN:001:COMPANY:user",301));
        var query=new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_USERS","fnd_user_info",List.of("7","7:OWN:001:COMPANY:user"));
        assertEquals(Set.of(100L,101L),new HashSet<>(mapper.selectIdentitySources(query).stream().map(row->row.getTargetId()).toList()));
        assertTrue(mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_USERS","fnd_user_info",List.of())).isEmpty());
    }
    @Test void roleAndScopeAliasQueriesKeepTheirOriginalObjectKinds(){
        bind(new Binding(1,2,1,"FND_ROLE","fnd_roles","6",201));
        bind(new Binding(2,2,1,"fnd_user_info","fnd_roles","6",101));
        bind(new Binding(3,2,1,"fnd_user_info","fnd_user_info","7",101));
        bind(new Binding(4,2,1,"fnd_user_info","fnd_user_info","7:OWN:001:COMPANY:user",301));
        var roles=mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_ROLES","fnd_roles",List.of("6")));
        assertEquals(List.of(201L),roles.stream().map(row->row.getTargetId()).toList());
        var scopes=mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_USER_SCOPES","fnd_user_info",List.of("7","7:OWN:001:COMPANY:user")));
        assertEquals(List.of(301L),scopes.stream().map(row->row.getTargetId()).toList());
    }
    // H2 executes the real MyBatis query; these aliases supply only MySQL's JSON scalar functions.
    public static String jsonExtract(String json,String path){
        if(json==null)return null;
        assertEquals("$._verifiedEhrEmployeeId",path);
        var node=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(json).path("_verifiedEhrEmployeeId");
        return node.isMissingNode()||node.isNull()?null:node.toString();
    }
    public static String jsonUnquote(String json){return json==null?null:cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(json).asText();}
    @Test void localEhrEmployeeLookupKeepsTenantConnectionAccountKindAndEmptySetBoundaries(){
        bind(new Binding(1,2,1,"fnd_user_info","fnd_user_info","7",101));
        bind(new Binding(2,3,2,"fnd_user_info","fnd_user_info","7",901));
        bind(new Binding(3,4,1,"fnd_user_info","fnd_user_info","7",902));
        bind(new Binding(4,2,1,"FND_USER_ROLE","fnd_user_info","7",201));
        bind(new Binding(5,2,1,"fnd_user_info","fnd_user_info","7:OWN:001:COMPANY:user",301));
        bind(new Binding(6,1,1,"fnd_user_info","fnd_user_info","7",401));
        jdbc.update("UPDATE int_sync_binding SET fields_json=?","{\"_verifiedEhrEmployeeId\":\"293\"}");
        assertEquals(List.of(101L),mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,9L,Set.of("293"))).stream().map(row->row.getTargetId()).toList());
        assertTrue(mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,9L,Set.of())).isEmpty());
        assertTrue(mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,9L,Set.of("294"))).isEmpty());
        jdbc.update("UPDATE int_sync_binding SET deleted=TRUE WHERE id=1");
        session.clearCache();assertTrue(mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,9L,Set.of("293"))).isEmpty());
    }
    @Test void directDeviceAliasKeepsTenantConnectionSourceAndObjectBoundaries() {
        jdbc.update("INSERT INTO int_sync_task(id,tenant_id,source_system,adapter,connection_id) VALUES(5,1,'DPPMS','DPPMS_DEVICE_BOOTSTRAP_DIRECT',9),(6,2,'DPPMS','DPPMS_DEVICE_BOOTSTRAP_DIRECT',9),(7,1,'DPPMS','DPPMS_DEVICE_BOOTSTRAP_DIRECT',10)");
        bind(new Binding(1,5,1,"DEVICE_BASE","ast_device_shipment","SN",101));
        bind(new Binding(2,5,1,"OTHER","ast_device_shipment","SN",201));
        bind(new Binding(3,6,2,"DEVICE_BASE","ast_device_shipment","SN",901));
        bind(new Binding(4,7,1,"DEVICE_BASE","ast_device_shipment","SN",902));
        var q=new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_DEVICE_BOOTSTRAP","ast_device_shipment",List.of("SN"));
        assertEquals(List.of(101L),mapper.selectIdentitySources(q).stream().map(row->row.getTargetId()).toList());
        assertTrue(mapper.selectIdentitySources(new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_DEVICE_BOOTSTRAP","ast_device_shipment",List.of())).isEmpty());
    }
    @Test void originalLineNeverConsumesIndependentReceiptsButDirectCompatibilityRemains() {
        jdbc.update("INSERT INTO int_sync_task(id,tenant_id,source_system,adapter,connection_id) VALUES(5,1,'DPPMS','DPPMS_ACCOUNT_FOUNDATION_DIRECT',9),(6,1,'DPPMS','DPPMS_ORGANIZATION_DIRECT',9)");
        bind(new Binding(1,2,1,"fnd_user_info","fnd_user_info","7",101));
        bind(new Binding(2,5,1,"fnd_user_info","fnd_user_info","7",201));
        bind(new Binding(3,6,1,"COMPANY_REFERENCE","t_company","8",301));
        jdbc.update("UPDATE int_sync_binding SET fields_json=?","{\"_verifiedEhrEmployeeId\":\"293\"}");
        var accounts=new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_USERS","fnd_user_info",List.of("7"));
        assertEquals(List.of(101L),mapper.selectIdentitySources(accounts.originalLine()).stream().map(row->row.getTargetId()).toList());
        assertEquals(2,mapper.selectIdentitySources(accounts).size());
        var org=new SyncQueries.IdentitySources(1L,"DPPMS",9L,"DPPMS_ORGANIZATION","t_company",List.of("8"));
        assertTrue(mapper.selectIdentitySources(org.originalLine()).isEmpty());
        assertEquals(1,mapper.selectIdentitySources(org).size());
        assertEquals(List.of(101L),mapper.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(1L,9L,Set.of("293")).originalLine()).stream().map(row->row.getTargetId()).toList());
    }
}
