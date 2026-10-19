package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.migration.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsAccountFoundationSyncAdapterTest {
    private final SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);
    private final SystemRoleMigrationApi roles=mock(SystemRoleMigrationApi.class);
    private final SystemUserRoleMigrationApi memberships=mock(SystemUserRoleMigrationApi.class);
    private final SystemUserScopeMigrationApi scopes=mock(SystemUserScopeMigrationApi.class);
    private final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    private final SystemDepartmentMigrationApi departments=mock(SystemDepartmentMigrationApi.class);
    private final cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi organizations=
            mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class);
    private DppmsAccountFoundationSyncAdapter adapter;
    private ValidatorFactory validation;
    private static final LocalDateTime HISTORY=LocalDateTime.of(2015,3,24,11,22);
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);validation=Validation.buildDefaultValidatorFactory();
        var tasks=mock(SyncTaskService.class);var companies=mock(CompanyApi.class);
        var definition=DppmsAccountFoundationSyncTemplate.create(9L);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter("DPPMS_ACCOUNT_FOUNDATION").setSourceSystem("DPPMS")
                .setConnectionId(9L).setDefinition(JsonUtils.toJsonString(definition)));
        when(companies.getCompanyByCode(anyString())).thenAnswer(call->{String code=call.getArgument(0);
            return new CompanyRespDTO().setId("001".equals(code)?11L:12L).setCode(code).setName("公司"+code).setStatus(0);});
        var userAdapter=new DppmsUserSyncAdapter(users,tasks,companies,departments,mock(PostApi.class),validation.getValidator());
        adapter=new DppmsAccountFoundationSyncAdapter(userAdapter,new DppmsRoleSyncAdapter(roles,tasks,bindings),
                new DppmsUserRoleSyncAdapter(memberships,tasks,bindings),new DppmsUserScopeSyncAdapter(scopes,departments,companies,tasks,bindings),tasks,bindings,
                organizations);
        when(users.preview(any())).thenAnswer(call->userResults(call.getArgument(0),null));
        when(roles.previewAfterAccounts(any())).thenAnswer(call->{SystemRoleMigrationApi.AccountPreview input=call.getArgument(0);
            assertEquals(Set.of(-1L),input.preceding().plannedUserIds());assertEquals("-1",input.request().entries().getFirst().definition().creator());
            return roleResults(input.request(),null);});
        when(memberships.previewAfterFoundation(any())).thenAnswer(call->{SystemUserRoleMigrationApi.FoundationPreview input=call.getArgument(0);
            assertEquals(-1L,input.request().entries().getFirst().userId());assertEquals(-2L,input.request().entries().getFirst().roleId());
            return membershipResults(input.request(),null);});
        when(scopes.previewAfterFoundation(any())).thenAnswer(call->{SystemUserScopeMigrationApi.FoundationPreview input=call.getArgument(0);
            assertEquals(Set.of(new SystemAccountMigrationPreview.Membership(-1L,"projectViewer")),input.preceding().plannedMemberships());
            return input.request().entries().stream().map(entry->new SystemUserScopeMigrationApi.Result(entry.object(),entry.sourceKey(),null,
                    new SystemUserScopeMigrationApi.Relation(null,entry.scope(),entry.audit(),0),"CREATED")).toList();});
    }
    @AfterEach void close(){validation.close();TenantContextHolder.clear();}
    private List<SystemUserMigrationApi.Result> userResults(SystemUserMigrationApi.Request input,Long id){
        return input.entries().stream().map(entry->new SystemUserMigrationApi.Result(entry.object(),entry.sourceKey(),null,
                new SystemUserMigrationApi.User(id,entry.profile(),new SystemUserMigrationApi.Audit(null,HISTORY,null,HISTORY)),"CREATED",null)).toList();
    }
    private List<SystemRoleMigrationApi.Result> roleResults(SystemRoleMigrationApi.Request input,Long id){
        return input.entries().stream().map(entry->new SystemRoleMigrationApi.Result(entry.object(),entry.sourceKey(),null,
                new SystemRoleMigrationApi.Role(id,entry.definition(),0,2,5,Set.of()),"CREATED",null)).toList();
    }
    private List<SystemUserRoleMigrationApi.Result> membershipResults(SystemUserRoleMigrationApi.Request input,Long id){
        return input.entries().stream().map(entry->new SystemUserRoleMigrationApi.Result(entry.object(),entry.sourceKey(),null,
                new SystemUserRoleMigrationApi.Relation(id,entry.userId(),entry.roleId()),"CREATED")).toList();
    }
    private Batch batch(){
        Map<String,Object> user=new LinkedHashMap<>();DppmsUserScopeSyncTemplate.columns().get("fnd_user_info").forEach(field->user.put(field,null));
        user.putAll(Map.of("id","7","username","c02791","realName","姓名","status",1,"createTime",HISTORY.toString(),"roleIds",";6;"));
        Map<String,Object> role=new LinkedHashMap<>();DppmsRoleSyncTemplate.create(9L).sources().getFirst().mappings().forEach(field->role.put(field.target(),null));
        role.putAll(Map.of("sourceRoleId","6","sourceCode","projectViewer","name","项目查看员","sort",106,"status",0,
                "creatorName","c02791","createTime",HISTORY.toString()));
        return new Batch("integration:1:12",List.of(new Row("fnd_user_info","7",user,null),new Row("FND_USER_ROLE","7",user,null),
                new Row("FND_ROLE","6",role,null)),List.of(),true,"RETAIN",false,"UPSERT",false);
    }
    @Test void firstImportPreviewsAllFourOwnersWithoutStoredUserOrRoleBindings(){
        var changes=adapter.preview(batch());
        assertEquals(5,changes.stream().filter(change->"CREATED".equals(change.action())).count());
        assertTrue(changes.stream().allMatch(change->change.targetId()==null));
        verify(users,never()).apply(any());verify(roles,never()).apply(any());verify(memberships,never()).apply(any());verify(scopes,never()).apply(any());
        verifyNoInteractions(bindings);
    }
    @Test void verifiedEhrIdentityIsOnlyLineageForLaterLocalDirectorResolution(){
        var input=batch();var raw=input.rows().getFirst().fields();
        raw.putAll(Map.of("migrationLoginCount","1","migrationEmployeeId","293","migrationLoginEmployeeId","293",
                "migrationEmployeeWorkNo","02791","migrationLoginWorkNo","02791","migrationEmployeeName","姓名","migrationLoginName","姓名"));
        var changes=adapter.preview(input);
        var account=changes.stream().filter(change->"fnd_user_info".equals(change.object()) && "7".equals(change.sourceKey())).findFirst().orElseThrow();
        assertEquals("293",account.after().get("_verifiedEhrEmployeeId"));assertNull(account.targetId());
        assertTrue(changes.stream().filter(change->!"fnd_user_info".equals(change.object())).noneMatch(change->change.after().containsKey("_verifiedEhrEmployeeId")));
        verifyNoInteractions(bindings);verify(users,never()).apply(any());
        raw.put("migrationLoginCount","2");clearInvocations(users);
        assertThrows(IllegalArgumentException.class,()->adapter.preview(input));verify(users,never()).preview(any());
    }
    @Test void finalScopeFailureRollsBackPrecedingOwnerWritesInTheSharedTransaction(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:account-foundation-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TABLE writes(kind VARCHAR(30))");
        when(users.apply(any())).thenAnswer(call->{jdbc.update("INSERT INTO writes VALUES('user')");return userResults(call.getArgument(0),101L);});
        when(roles.apply(any())).thenAnswer(call->{jdbc.update("INSERT INTO writes VALUES('role')");return roleResults(call.getArgument(0),201L);});
        when(memberships.apply(any())).thenAnswer(call->{SystemUserRoleMigrationApi.Request input=call.getArgument(0);
            assertEquals(101L,input.entries().getFirst().userId());assertEquals(201L,input.entries().getFirst().roleId());
            jdbc.update("INSERT INTO writes VALUES('membership')");return membershipResults(input,301L);});
        when(scopes.apply(any())).thenThrow(new IllegalArgumentException("范围历史冲突"));
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->adapter.apply(batch())));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
        verify(scopes).apply(any());verify(roles,never()).previewAfterAccounts(any());verify(memberships,never()).previewAfterFoundation(any());
    }
    @Test void templateUsesFourQueriesAndExistingOwnerContracts(){
        var template=DppmsAccountFoundationSyncTemplate.create(9L);new SyncDefinitionValidator(List.of(adapter)).validate(template);
        assertEquals(4,template.sources().stream().filter(source->source.queryRef()==null).count());
        assertFalse(template.clearBeforeLoad());assertFalse(template.resetMappingsBeforeLoad());
    }
    @Test void localOrganizationAliasesValidateTypedIdsEvenWhenCompanyAndDepartmentIdsCoincide(){
        var company=new cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO().setId(11L).setCode("001").setName("公司001");
        var department=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO().setId(11L).setCode("162102").setName("部门");
        when(organizations.list()).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Node(company,null,null),
                new cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Node(null,department,null)));
        when(departments.getCatalogue()).thenReturn(List.of(new SystemDepartmentMigrationApi.Department(11L,"162102","部门",0L,0,null,null,null,0,0,null,null,null,null)));
        when(bindings.selectIdentitySources(any())).thenAnswer(call->{cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries.IdentitySources query=call.getArgument(0);
            assertEquals("DPPMS_ORGANIZATION",query.adapter());assertEquals(9L,query.connectionId());
            return List.of(new cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO().setSourceKey("t_company".equals(query.sourceObject())?"7":"8")
                    .setTargetId(11L).setFieldsJson("t_company".equals(query.sourceObject())?"{\"sourceCompanyCode\":\"01\"}":"{\"code\":\"162102\"}"));});
        Map<String,Object> account=new LinkedHashMap<>(),info=new LinkedHashMap<>();
        DppmsUserScopeSyncTemplate.columns().get("t_user").forEach(field->account.put(field,null));
        DppmsUserScopeSyncTemplate.columns().get("t_user_info").forEach(field->info.put(field,null));
        account.putAll(Map.of("user_id","20","user_name","c02791","status",1,"create_time",HISTORY.toString()));
        info.putAll(Map.of("id","30","user_id","20","compID","7","depID","8"));
        var base=batch();var rows=new ArrayList<>(base.rows());rows.add(new Row("t_user","20",account,null));rows.add(new Row("t_user_info","30",info,null));
        var input=new Batch(base.owner(),rows,base.bindings(),true,"RETAIN",false,"UPSERT",false);
        var reachedOwner=new IllegalStateException("validated local aliases");
        doAnswer(call->{SystemUserMigrationApi.Request request=call.getArgument(0);
            assertEquals(11L,request.entries().getFirst().profile().departmentId());throw reachedOwner;}).when(users).preview(any());
        assertSame(reachedOwner,assertThrows(IllegalStateException.class,()->adapter.preview(input)));
        department.setCode("changed");clearInvocations(users);
        assertThrows(IllegalArgumentException.class,()->adapter.preview(input));verifyNoInteractions(users);
    }
}
