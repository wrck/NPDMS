package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.system.api.migration.SystemUserRoleMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsUserRoleSyncAdapterTest {
    private SystemUserRoleMigrationApi owner;
    private SyncBindingMapper bindings;
    private DppmsUserRoleSyncAdapter adapter;
    private SystemUserRoleMigrationApi.Request request;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);owner=mock(SystemUserRoleMigrationApi.class);bindings=mock(SyncBindingMapper.class);
        var tasks=mock(SyncTaskService.class);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter("DPPMS_USER_ROLES").setSourceSystem("DPPMS").setConnectionId(9L));
        when(bindings.selectIdentitySources(any())).thenAnswer(invocation->{
            SyncQueries.IdentitySources query=invocation.getArgument(0);
            assertEquals(1L,query.tenantId());assertEquals(9L,query.connectionId());assertEquals("DPPMS",query.sourceSystem());
            return query.sourceKeys().stream().map(key->new SyncBindingDO().setSourceKey(key)
                    .setTargetId("DPPMS_USERS".equals(query.adapter())?100L:"1".equals(key)?200L:201L).setFieldsJson("{\"_auditOnly\":false}")).toList();
        });
        when(owner.preview(any())).thenAnswer(invocation->{
            request=invocation.getArgument(0);
            return request.entries().stream().map(entry->new SystemUserRoleMigrationApi.Result(entry.object(),entry.sourceKey(),null,
                    new SystemUserRoleMigrationApi.Relation(entry.targetId(),entry.userId(),entry.roleId()),"CREATED")).toList();
        });
        adapter=new DppmsUserRoleSyncAdapter(owner,tasks,bindings);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private Row tRole(String key,String username,String roleId,Object company) {
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("id",key);fields.put("user_id","10");fields.put("role_id",roleId);fields.put("comp_id",company);fields.put("username",username);
        return new Row("T_USER_ROLE",key,fields,null);
    }
    private Row fnd(String key,String username,String roles) {
        Map<String,Object> fields=new LinkedHashMap<>();DppmsUserRoleSyncTemplate.COLUMNS.get("FND_USER_ROLE").forEach(column->fields.put(column,null));
        fields.put("id",key);fields.put("username",username);fields.put("roleIds",roles);return new Row("FND_USER_ROLE",key,fields,null);
    }
    private Batch batch(List<Row> rows){return new Batch("integration:1:12",rows,List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void approvedNullCompanyRelationshipsGoToFormalRolesWithoutAnyCompanyGrant() {
        var changes=adapter.preview(batch(List.of(tRole("271","z04214","2",null))));
        assertEquals(1,request.entries().size());assertEquals("user",request.entries().getFirst().expectedRoleCode());
        assertEquals(100L,request.entries().getFirst().userId());assertEquals(201L,request.entries().getFirst().roleId());
        assertTrue(changes.getFirst().after().containsKey("sourceCompanyId"));assertNull(changes.getFirst().after().get("sourceCompanyId"));
        assertFalse(changes.getFirst().after().containsKey("companyId"));verify(owner,never()).apply(any());
    }
    @Test void stringRoleValuesAreConvertedAndDisabledRoleFifteenIsExplicitlyExcluded() {
        var changes=adapter.preview(batch(List.of(fnd("10","c02791",";3;,;11;,;12;,;15;"))));
        assertEquals(Set.of("user","SERVICE_MANAGER","PROJECT_MANAGER"),new HashSet<>(request.entries().stream().map(SystemUserRoleMigrationApi.Entry::expectedRoleCode).toList()));
        assertTrue(request.entries().stream().allMatch(entry->entry.sourceKey().startsWith("10:")));
        assertEquals(1,changes.stream().filter(change->"SKIPPED".equals(change.action())).count());
        assertEquals(Set.of("3","11"),DppmsUserRoleSyncAdapter.roleKeys(";3;11;3;"));
        assertEquals(Set.of("3","11"),DppmsUserRoleSyncAdapter.roleKeys(";3;,;11;,;3;"));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(fnd("10","c02791",";999;")))));
        assertThrows(IllegalArgumentException.class,()->DppmsUserRoleSyncAdapter.roleKeys(";ROLE_COMMON;"));
    }
    @Test void platformSuperAdminIsOnlyAssignedThroughApprovedTSourceAndExcludedAdminCannotGetOldRoles() {
        adapter.preview(batch(List.of(tRole("1","c02791","1",1),tRole("2","admin","1",-1))));
        assertEquals(1,request.entries().size());assertEquals("super_admin",request.entries().getFirst().expectedRoleCode());
        adapter.preview(batch(List.of(fnd("12","c02791",";1;"))));
        assertEquals("admin",request.entries().getFirst().expectedRoleCode());
    }
    @Test void missingBindingsAndAuditOnlyAccountsBlockBeforePermissionsOwner() {
        doReturn(List.of()).when(bindings).selectIdentitySources(any());
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(tRole("1","c02791","2",1)))));
        doReturn(List.of(new SyncBindingDO().setSourceKey("10").setTargetId(1L).setFieldsJson("{\"_auditOnly\":true}"))).when(bindings).selectIdentitySources(any());
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(tRole("1","c02791","2",1)))));
        verifyNoInteractions(owner);
    }
    @Test void allOriginalRelationshipFieldsAndNoncredentialAccountColumnsPassReadOnlySqlCompilation() {
        var template=DppmsUserRoleSyncTemplate.create(9L);
        assertEquals("DPPMS_USER_ROLES",template.adapter());assertFalse(template.clearBeforeLoad());assertFalse(template.autoPaging());
        for(var source:template.sources()) {
            if(SyncSourceEvidenceReader.reference(source))assertDoesNotThrow(()->SyncSourceEvidenceReader.validate(template,source));
            else {assertDoesNotThrow(()->MysqlSyncReader.compile(source));assertFalse(source.sql().contains("password"));}
        }
        assertTrue(template.sources().getFirst().sql().contains("r.comp_id"));
        assertEquals("DPPMS_USERS",template.sources().getLast().evidenceAdapter());
        assertTrue(template.sources().getLast().mappings().stream().anyMatch(mapping->"roleIds".equals(mapping.source())));
    }
}
