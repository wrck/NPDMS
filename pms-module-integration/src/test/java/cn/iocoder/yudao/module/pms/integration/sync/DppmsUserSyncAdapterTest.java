package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsUserSyncAdapterTest {
    private SystemUserMigrationApi owner;
    private CompanyApi companies;
    private SystemDepartmentMigrationApi departments;
    private DppmsUserSyncAdapter adapter;
    private Request request;
    private ValidatorFactory validatorFactory;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        owner = mock(SystemUserMigrationApi.class); companies = mock(CompanyApi.class);
        var tasks = mock(SyncTaskService.class);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter("DPPMS_USERS").setSourceSystem("DPPMS").setConnectionId(9L));
        when(owner.preview(any())).thenAnswer(invocation -> {
            request = invocation.getArgument(0);
            List<Result> result = new ArrayList<>();
            for (Entry entry : request.entries()) result.add(new Result(entry.object(), entry.sourceKey(), null,
                    new User(entry.targetId(), entry.profile(), new Audit(null, entry.audit().createTime(), null, entry.audit().updateTime())), "CREATED", null));
            return result;
        });
        validatorFactory = Validation.buildDefaultValidatorFactory();
        departments = mock(SystemDepartmentMigrationApi.class);
        adapter = new DppmsUserSyncAdapter(owner, tasks, companies, departments, mock(PostApi.class), validatorFactory.getValidator());
    }
    @AfterEach void clear() { validatorFactory.close(); TenantContextHolder.clear(); }
    @Test void historicalEmploymentReferencesDisabledDepartmentWithoutChangingItsStatusOrGrantingScope() {
        var disabled = new SystemDepartmentMigrationApi.Department(71L,"162028","安全研究院",1L,0,null,null,null,1,0,null,null,null,null);
        when(departments.getCatalogue()).thenReturn(List.of(disabled));
        Row account = fnd("7","c02791","姓名");
        var detail=employment("162028");
        when(companies.getCompanyByCode("001")).thenReturn(new CompanyRespDTO().setId(81L));
        adapter.preview(batch(List.of(account,account("t_user","9","c02791",null),detail)));
        assertEquals(71L,request.entries().getFirst().profile().departmentId());
        assertEquals(1,disabled.status());
        verify(departments,never()).apply(any());
    }
    @Test void duplicateFormalDepartmentCodesBlockBeforeUserOwner() {
        var first = new SystemDepartmentMigrationApi.Department(71L,"162028","旧部门",1L,0,null,null,null,0,0,null,null,null,null);
        var second = new SystemDepartmentMigrationApi.Department(72L,"162028","另一部门",1L,0,null,null,null,1,0,null,null,null,null);
        when(departments.getCatalogue()).thenReturn(List.of(first,second));
        Row account=fnd("7","c02791","姓名");
        when(companies.getCompanyByCode("001")).thenReturn(new CompanyRespDTO().setId(81L));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(account,account("t_user","9","c02791",null),employment("162028")))));
        verifyNoInteractions(owner);
    }
    private Row employment(String departmentCode) {
        var values=fields("t_user_info");values.put("id","19");values.put("user_id","9");
        values.put("migrationCompanyCode","01");values.put("migrationDepartmentCode",departmentCode);
        return new Row("t_user_info","19",values,null);
    }
    @Test void missingFormalDepartmentBlocksInsteadOfSilentlyRemovingHistoricalEmployment() {
        when(companies.getCompanyByCode("001")).thenReturn(new CompanyRespDTO().setId(81L));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(fnd("7","c02791","姓名"),account("t_user","9","c02791",null),employment("missing")))));
        verifyNoInteractions(owner);
    }
    private Map<String,Object> fields(String table) {
        Map<String,Object> fields = new LinkedHashMap<>();
        DppmsUserSyncTemplate.COLUMNS.get(table).forEach(column -> fields.put(column,null));
        if (!"t_user_info".equals(table)) {
            fields.put("status",1);
            fields.put("t_user".equals(table) ? "create_time" : "createTime", "2015-03-24 11:22:00");
        }
        return fields;
    }
    private Row account(String table, String id, String name, String realName) {
        var values = fields(table);
        values.put("t_user".equals(table) ? "user_id" : "id", id);
        values.put("t_user".equals(table) ? "user_name" : "username", name);
        if ("fnd_user_info".equals(table)) values.put("realName",realName);
        return new Row(table,id,values,null);
    }
    private Row fnd(String id,String name,String realName) { return account("fnd_user_info",id,name,realName); }
    private Batch batch(List<Row> rows) { return new Batch("integration:1:12",rows,List.of(),true,"RETAIN",false,"UPSERT",false); }
    private void ehr(Row row,String empId,String workNo,String employeeName,String loginName) {
        row.fields().put("migrationLoginCount",1L);
        row.fields().put("migrationLoginEmployeeId",empId);
        row.fields().put("migrationLoginWorkNo",workNo);
        row.fields().put("migrationLoginName",loginName);
        row.fields().put("migrationEmployeeId",empId);
        row.fields().put("migrationEmployeeWorkNo",workNo);
        row.fields().put("migrationEmployeeName",employeeName);
    }
    @Test void adjudicatedEmailAndEhrExceptionsCarryToFormalFieldsWithoutInferringWorkNumbers() {
        Row invalid = fnd("117","w30049","历史姓名"); invalid.fields().put("email","@dptech.com");
        Row normalized = fnd("862","zhaoyangxi_supp","赵阳熙"); normalized.fields().put("email","same@example.invalid\t");
        Row existingFormat = fnd("10","liuqian_ss","刘谦"); existingFormat.fields().put("email","liuqian_ss@dptech");
        Row name = fnd("145","w00343","王俊柯"); ehr(name,"74","00343","王俊珂","王俊珂");
        Row employee = fnd("170","l02826","乐征侨"); ehr(employee,"2770","02826","乐征侨","乐征桥");
        adapter.preview(batch(List.of(invalid,normalized,name,employee,existingFormat)));
        Map<String,Profile> profiles = new HashMap<>(); request.entries().forEach(entry -> profiles.put(entry.profile().username(),entry.profile()));
        assertEquals("@dptech.com",profiles.get("w30049").email()); assertNull(profiles.get("w30049").employeeNo());
        assertEquals("same@example.invalid",profiles.get("zhaoyangxi_supp").email());
        assertEquals("liuqian_ss@dptech",profiles.get("liuqian_ss").email());
        assertEquals("王俊珂",profiles.get("w00343").nickname()); assertEquals("00343",profiles.get("w00343").employeeNo());
        assertEquals("乐征侨",profiles.get("l02826").nickname()); assertEquals("02826",profiles.get("l02826").employeeNo());
        assertEquals("same@example.invalid\t",normalized.fields().get("email"));
    }
    @Test void fndPrimaryAndAdjudicatedDisabledDuplicatesShareOneProfileButKeepEverySourceIdentity() {
        Row first = fnd("233","c00733","程云"), second = fnd("408","c00733","程云");
        first.fields().put("status",0); second.fields().put("status",0);
        Row legacy = account("t_user","99","c00733",null); legacy.fields().put("status",1);
        adapter.preview(batch(List.of(first,second,legacy)));
        assertEquals(3,request.entries().size());
        Entry primary = request.entries().stream().filter(entry -> !entry.referenceOnly()).findFirst().orElseThrow();
        assertEquals("408",primary.sourceKey()); assertEquals(1,primary.profile().status());
        assertTrue(request.entries().stream().allMatch(entry -> entry.profile().equals(primary.profile())));
        assertEquals(primary.audit().createTime(),primary.audit().updateTime());
        assertNull(second.fields().get("updateTime"));
    }
    @Test void excludedAdminIsAnAuditReferenceAndExcludedProfilesDoNotAcquireBindings() {
        Row selected = fnd("145","c00999","原姓名"); selected.fields().put("createBy","admin");
        Row admin = fnd("81","admin","旧管理员"), legacyAdmin = account("t_user","0","admin",null);
        Row excluded = fnd("473","pms_tbpublic","旧公共账号");
        var changes = adapter.preview(batch(List.of(selected,admin,legacyAdmin,excluded)));
        assertEquals(1,request.entries().size()); assertEquals(2,request.auditReferences().size());
        assertTrue(request.auditReferences().stream().allMatch(reference -> reference.userId()==1L));
        assertEquals("fnd_user_info:81",request.entries().getFirst().audit().creatorIdentity());
        assertEquals("SKIPPED",changes.stream().filter(change -> "473".equals(change.sourceKey())).findFirst().orElseThrow().action());
        verify(owner,never()).apply(any());
    }
    @Test void unknownEmployeeIdentityCompanyOrOriginalColumnBlocksBeforeOwnerReceivesAnything() {
        Row mismatch = fnd("5","c02791","旧姓名"); ehr(mismatch,"99","02791","另一人","另一人");
        assertThrows(IllegalArgumentException.class,() -> adapter.preview(batch(List.of(mismatch))));
        Row account = account("t_user","5","c02791",null);
        var values = fields("t_user_info"); values.put("id","6"); values.put("user_id","5"); values.put("migrationCompanyCode","99");
        assertThrows(IllegalArgumentException.class,() -> adapter.preview(batch(List.of(account,new Row("t_user_info","6",values,null)))));
        Row missing = fnd("7","c02791","姓名"); missing.fields().remove("customInfo");
        assertThrows(IllegalArgumentException.class,() -> adapter.preview(batch(List.of(missing))));
        verifyNoInteractions(owner);
    }
    @Test void roundTrippedBindingsKeepFormalProfileAndHistoricalTimesForOwnersReplayProtection() {
        Row row = fnd("7","c02791","姓名");
        var change = adapter.preview(batch(List.of(row))).getFirst();
        Map<String,Object> stored = JsonUtils.parseMap(JsonUtils.toJsonString(change.after()));
        Row bound = new Row(row.object(),row.sourceKey(),row.fields(),99L);
        adapter.preview(new Batch("integration:1:12",List.of(bound),List.of(new Binding(row.object(),row.sourceKey(),99L,stored)),true,"RETAIN",false,"UPSERT",false));
        assertEquals(99L,request.entries().getFirst().lastApplied().id());
        assertEquals(request.entries().getFirst().profile(),request.entries().getFirst().lastApplied().profile());
        assertEquals(request.entries().getFirst().audit().createTime(),request.entries().getFirst().lastApplied().audit().createTime());
    }
}
