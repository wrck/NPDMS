package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsUserScopeSyncAdapterTest {
    private DppmsUserScopeSyncAdapter adapter;
    private SystemUserScopeMigrationApi owner;
    private CompanyApi companies;
    private SystemUserScopeMigrationApi.Request request;
    private final Map<String,String> usernames=new HashMap<>();
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);owner=mock(SystemUserScopeMigrationApi.class);companies=mock(CompanyApi.class);
        var tasks=mock(SyncTaskService.class);var bindings=mock(SyncBindingMapper.class);var departments=mock(SystemDepartmentMigrationApi.class);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter("DPPMS_USER_SCOPES").setSourceSystem("DPPMS").setConnectionId(9L));
        when(companies.getCompanyByCode(anyString())).thenAnswer(invocation->{String code=invocation.getArgument(0);return new CompanyRespDTO().setId("001".equals(code)?11L:12L).setCode(code).setName("公司"+code).setStatus(0);});
        when(departments.getCatalogue()).thenReturn(List.of(department("162102",3L),department("323000",4L),department("280500",5L),department("310500001001",6L)));
        when(bindings.selectIdentitySources(any())).thenAnswer(invocation->{SyncQueries.IdentitySources query=invocation.getArgument(0);
            assertEquals(1L,query.tenantId());assertEquals(9L,query.connectionId());assertEquals("DPPMS_USERS",query.adapter());
            return query.sourceKeys().stream().map(key->new SyncBindingDO().setSourceKey(key).setTargetId(100L)
                    .setFieldsJson(JsonUtils.toJsonString(Map.of("profile",Map.of("username",usernames.get(query.sourceObject()+":"+key)),"_auditOnly",false)))).toList();});
        when(owner.preview(any())).thenAnswer(invocation->{request=invocation.getArgument(0);return request.entries().stream().map(entry->new SystemUserScopeMigrationApi.Result(
                entry.object(),entry.sourceKey(),null,new SystemUserScopeMigrationApi.Relation(null,entry.scope(),entry.audit(),0),"CREATED")).toList();});
        adapter=new DppmsUserScopeSyncAdapter(owner,departments,companies,tasks,bindings);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private SystemDepartmentMigrationApi.Department department(String code,Long id){return new SystemDepartmentMigrationApi.Department(id,code,"部门"+code,0L,0,null,null,null,0,0,null,null,null,null);}
    private Row row(String object,String key,Map<String,Object> values){
        Map<String,Object> fields=new LinkedHashMap<>();DppmsUserScopeSyncTemplate.columns().get(object).forEach(column->fields.put(column,null));
        fields.put("t_user".equals(object)?"user_id":"id",key);fields.putAll(values);
        if("fnd_user_info".equals(object)||"t_user".equals(object))usernames.put(object+":"+key,Objects.toString(fields.get("t_user".equals(object)?"user_name":"username"),null));
        return new Row(object,key,fields,null);
    }
    private Row fnd(String name,String office){return row("fnd_user_info","10",Map.of("username",name,"dpNo",office,"roleIds",";3;","status",1,"createTime","2015-05-20 15:40:05","effectiveFrom","2015-05-20 15:40:05"));}
    private Batch batch(Row... rows){return new Batch("integration:1:12",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void ownAndAdditionalGrantsKeepApprovedFlagsHistoricalDatesAndForeignKeyAssociation(){
        Row account=fnd("c02791","311202");Row power=row("fnd_user_power","470",Map.of("fndUserId","10","username","wrong_redundant_name","areapower","311202",
                "createTime","2015-05-20 15:40:05","effectiveFrom","2015-05-20 15:40:05"));
        adapter.preview(batch(account,power));assertEquals(4,request.entries().size());
        assertTrue(request.entries().stream().allMatch(entry->entry.scope().userId()==100L && "162102".equals(entry.scope().departmentCode())));
        assertEquals(Set.of("001","002"),new HashSet<>(request.entries().stream().map(entry->entry.scope().companyCode()).toList()));
        assertTrue(request.entries().stream().filter(entry->entry.object().equals("fnd_user_info")).allMatch(entry->entry.scope().primary()));
        assertTrue(request.entries().stream().filter(entry->entry.object().equals("fnd_user_power")).noneMatch(entry->entry.scope().primary()));
        assertTrue(request.entries().stream().allMatch(entry->entry.audit().createTime().equals(entry.audit().updateTime())));verify(owner,never()).apply(any());
    }
    @Test void fndIsPrimaryAndDifferentTOfficeSupplementsWithinExplicitTCompanyScope(){
        Row fnd=fnd("c01034","280500");Row t=row("t_user","20",Map.of("user_name","c01034","status",1,"create_time","2015-05-20 15:40:05"));
        Row info=row("t_user_info","30",Map.of("user_id","20","custom3","323000"));
        Row role=row("t_user_role","40",Map.of("user_id","20","role_id","2","comp_id",7,"username","c01034","migrationCompanyCode","01"));
        adapter.preview(batch(fnd,t,info,role));assertEquals(2,request.entries().size());
        assertTrue(request.entries().stream().allMatch(entry->entry.scope().companyId()==11L));
        assertTrue(request.entries().stream().filter(entry->entry.object().equals("fnd_user_info")).allMatch(entry->entry.scope().primary()));
        assertFalse(request.entries().stream().filter(entry->entry.object().equals("t_user_info")).findFirst().orElseThrow().scope().primary());
    }
    @Test void nullCompanyRoleOnlyImportAndExplicitRolelessAccountExclusionNeverGrantScopes(){
        Row t=row("t_user","20",Map.of("user_name","z04214","status",1,"create_time","2015-05-20 15:40:05"));
        Row info=row("t_user_info","30",Map.of("user_id","20","custom3","323000"));
        Row role=row("t_user_role","271",Map.of("user_id","20","role_id","2","username","z04214"));
        adapter.preview(batch(t,info,role));assertTrue(request.entries().isEmpty());verifyNoInteractions(companies);
        Row fndAlso=fnd("z04214","162102");adapter.preview(batch(t,info,role,fndAlso));assertTrue(request.entries().isEmpty());verifyNoInteractions(companies);
        Row excluded=fnd("konglinghui_supp","999999");excluded.fields().put("roleIds",null);
        var changes=adapter.preview(batch(excluded));assertTrue(request.entries().isEmpty());assertTrue(changes.getFirst().message().contains("用户明确"));
    }
    @Test void unmappedOfficeAndUnknownCompanyOrRoleCannotFallBackToParentOrDefaultRole(){
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(fnd("c02791","310500001009"))));
        Row bad=fnd("c02791","162102");bad.fields().put("roleIds",";999;");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(bad)));
        Row account=row("t_user","20",Map.of("user_name","z04214","status",1,"create_time","2015-05-20 15:40:05"));
        Row role=row("t_user_role","40",Map.of("user_id","20","role_id","2","comp_id",7,"migrationCompanyCode","99","username","z04214"));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(account,role)));
    }
    @Test void orphanPersonnelReferencesStayUnassociatedWithoutBreakingTheWholeSnapshot(){
        Row info=row("t_user_info","30",Map.of("user_id","999","custom3","162102"));
        var changes=adapter.preview(batch(info));assertTrue(request.entries().isEmpty());assertEquals("SKIPPED",changes.getFirst().action());
    }
    @Test void emptyAdditionalOfficeDeclarationsNeverGenerateAnUnrestrictedScope(){
        Row account=fnd("c02791","162102");
        Row power=row("fnd_user_power","470",Map.of("fndUserId","10","username","c02791","areapower","",
                "createTime","2015-05-20 15:40:05","effectiveFrom","2015-05-20 15:40:05"));
        var changes=adapter.preview(batch(account,power));
        assertEquals(2,request.entries().size());
        assertTrue(request.entries().stream().allMatch(entry->entry.object().equals("fnd_user_info") && entry.scope().departmentId()!=null));
        assertTrue(changes.stream().anyMatch(change->change.object().equals("fnd_user_power") && change.message().contains("空范围没有附加授权事实")));
    }
    @Test void adjudicatedOrphanExclusionDoesNotExcludeNewUnresolvedPersonnelRecords(){
        Row excluded=row("t_user_info","1000186",Map.of("workNo","test"));
        Row unknown=row("t_user_info","1000190",Map.of("workNo","test"));
        var changes=adapter.preview(batch(excluded,unknown));assertTrue(request.entries().isEmpty());
        assertTrue(changes.getFirst().message().contains("用户明确"));assertTrue(changes.getLast().message().contains("待关联"));
    }
}
