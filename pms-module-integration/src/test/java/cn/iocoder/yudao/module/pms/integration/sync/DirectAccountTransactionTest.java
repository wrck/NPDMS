package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectAccountTransactionTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DppmsDirectAccountTask task;
    DirectAccountMembershipMapper memberships;
    ValidatorFactory validation;
    final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final SystemDepartmentMigrationApi departments=mock(SystemDepartmentMigrationApi.class);
    final OAuth2TokenCommonApi tokens=mock(OAuth2TokenCommonApi.class);
    static final String HISTORY="2015-03-24T11:22:00";

    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);validation=Validation.buildDefaultValidatorFactory();
        var source=new DriverManagerDataSource("jdbc:h2:mem:p04-direct-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("sql/direct-account-tables.sql")).execute(source);
        jdbc=new JdbcTemplate(source);
        jdbc.update("INSERT INTO system_tenant(id,account_count) VALUES(1,1000)");
        jdbc.update("INSERT INTO system_company(id,tenant_id,code,name,status) VALUES(11,1,'001','公司001',0),(12,1,'002','公司002',0)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(String name:List.of("User","Role","Membership","Scope","Auxiliary")) {
            String resource="mapper/sync/DirectAccount"+name+"Mapper.xml";
            try(var input=new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        memberships=session.getMapper(DirectAccountMembershipMapper.class);
        var persistence=new DirectAccountPersistence(session.getMapper(DirectAccountUserMapper.class),session.getMapper(DirectAccountRoleMapper.class),
                memberships,session.getMapper(DirectAccountScopeMapper.class),session.getMapper(DirectAccountAuxiliaryMapper.class),
                new BCryptPasswordEncoder(4),new TenantProperties(),tokens,source,mock(CacheManager.class));
        var tasks=mock(SyncTaskService.class);var companies=mock(CompanyApi.class);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter(DppmsDirectAccountTemplate.ADAPTER).setSourceSystem("DPPMS")
                .setConnectionId(9L).setDefinition(JsonUtils.toJsonString(DppmsDirectAccountTemplate.create(9L))));
        when(companies.getCompanyByCode(anyString())).thenAnswer(call->{String code=call.getArgument(0);
            return new CompanyRespDTO().setId("001".equals(code)?11L:12L).setCode(code).setName("公司"+code);});
        task=new DppmsDirectAccountTask(persistence,tasks,bindings,mock(ManagedOrganizationApi.class),companies,departments,mock(PostApi.class),validation.getValidator());
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void close(){validation.close();TenantContextHolder.clear();}
    Batch batch() {
        Map<String,Object> user=new LinkedHashMap<>();DppmsUserScopeSyncTemplate.columns().get("fnd_user_info").forEach(field->user.put(field,null));
        user.putAll(Map.of("id","7","username","c02791","realName","姓名","status",1,"createTime",HISTORY,"roleIds",";6;"));
        Map<String,Object> role=new LinkedHashMap<>();DppmsRoleSyncTemplate.create(9L).sources().getFirst().mappings().forEach(field->role.put(field.target(),null));
        role.putAll(Map.of("sourceRoleId","6","sourceCode","projectViewer","name","项目查看员","sort",106,"status",0,"creatorName","c02791","createTime",HISTORY));
        return new Batch("integration:1:12",List.of(new Row("fnd_user_info","7",user,null),new Row("FND_USER_ROLE","7",user,null),
                new Row("FND_ROLE","6",role,null)),List.of(),true,"RETAIN",false,"UPSERT",false);
    }
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    void assertNoWrites(){for(String table:List.of("system_users","system_role","system_user_role","system_user_company_department_scope"))assertEquals(0,count(table),table);}
    @Test void initialPreviewUsesRealSqlWithoutWritingAndApplyRequiresTransaction() {
        var changes=task.preview(batch());assertEquals(5,changes.size());assertTrue(changes.stream().allMatch(c->c.targetId()==null));
        assertNoWrites();assertThrows(IllegalStateException.class,()->task.apply(batch()));assertNoWrites();
        verifyNoInteractions(tokens);verify(departments,never()).apply(any());
    }
    @Test void directFourStageWritePreservesAuditAndReplaysWithoutDuplicates() {
        var changes=tx.execute(status->task.apply(batch()));
        assertEquals(1,count("system_users"));assertEquals(1,count("system_role"));assertEquals(1,count("system_user_role"));assertEquals(2,count("system_user_company_department_scope"));
        assertEquals(0,count("system_role_menu"));
        assertNull(jdbc.queryForObject("SELECT updater FROM system_users",String.class));
        long userId=jdbc.queryForObject("SELECT id FROM system_users",Long.class);
        assertEquals(Long.toString(userId),jdbc.queryForObject("SELECT creator FROM system_role",String.class));
        assertEquals(60,jdbc.queryForObject("SELECT LENGTH(password) FROM system_users",Integer.class));
        var prior=changes.stream().map(c->new Binding(c.object(),c.sourceKey(),c.targetId(),c.after())).toList();
        var ids=new HashMap<String,Long>();changes.forEach(c->ids.put(c.object()+":"+c.sourceKey(),c.targetId()));
        var input=batch();var rows=input.rows().stream().map(row->new Row(row.object(),row.sourceKey(),row.fields(),ids.get(row.object()+":"+row.sourceKey()))).toList();
        var replay=new Batch(input.owner(),rows,prior,true,"RETAIN",false,"UPSERT",false);
        var result=tx.execute(status->task.apply(replay));assertTrue(result.stream().allMatch(c->"UNCHANGED".equals(c.action())));
        assertEquals(2,count("system_user_company_department_scope"));verifyNoInteractions(tokens);
    }
    @Test void lastScopeFailureRollsBackAllEarlierActualSqlWrites() {
        jdbc.execute("ALTER TABLE system_user_company_department_scope ADD CONSTRAINT reject_second_scope CHECK(company_code <> '002')");
        assertThrows(RuntimeException.class,()->tx.execute(status->task.apply(batch())));assertNoWrites();
    }
    @Test void quotaCrossTenantAndEmptyReferenceSetsRemainEnforced() {
        jdbc.update("UPDATE system_tenant SET account_count=0 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->task.preview(batch()));assertNoWrites();
        assertTrue(memberships.selectUserIds(new DirectAccountQueries.UserRoleMigrationQuery(1L,Set.of(),Set.of())).isEmpty());
        jdbc.update("UPDATE system_tenant SET account_count=1000 WHERE id=1");
        jdbc.update("UPDATE system_company SET tenant_id=2 WHERE id=12");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch())));assertNoWrites();
    }
    @Test void taskTypesKeepSeparateTemplatesAndNativeSourceKeys() {
        var original=DppmsAccountFoundationSyncTemplate.create(9L);var direct=DppmsDirectAccountTemplate.create(9L);
        new SyncDefinitionValidator(List.of(task)).validate(direct);
        assertEquals("DPPMS_ACCOUNT_FOUNDATION",original.adapter());assertEquals(4,original.sources().stream().filter(s->s.queryRef()==null).count());
        assertEquals(7,direct.sources().stream().filter(s->s.queryRef()==null).count());
        for(var source:direct.sources())if(source.queryRef()==null) {
            assertNull(source.updatedAt());assertTrue(Set.of("sourceId","role_id","id","_groupKey").contains(SyncSourceProjection.readKey(source)));
        }
    }
}
