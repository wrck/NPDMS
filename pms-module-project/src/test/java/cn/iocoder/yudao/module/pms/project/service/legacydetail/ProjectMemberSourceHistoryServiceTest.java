package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMemberSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.customer.api.contact.FinalCustomerContactMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectMemberSourceHistoryServiceTest {
    ProjectManualCreationService access;ProjectMasterMapper projects;ProjectMemberHistoryMigrationMapper members;
    SystemUserMigrationApi identities;FinalCustomerContactMigrationApi contacts;PermissionApi permissions;ProjectMemberSourceHistoryService service;
    Map<String,String> raw;List<ProjectMemberAssignmentDO> stored;ProjectMasterDO project;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);access=mock(ProjectManualCreationService.class);projects=mock(ProjectMasterMapper.class);members=mock(ProjectMemberHistoryMigrationMapper.class);
        identities=mock(SystemUserMigrationApi.class);contacts=mock(FinalCustomerContactMigrationApi.class);permissions=mock(PermissionApi.class);
        service=new ProjectMemberSourceHistoryService(access,projects,members,identities,contacts,permissions);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        project=new ProjectMasterDO();project.setId(10L);project.setTenantId(1L);project.setVersion(4L);project.setLifecycleStatus("ACTIVE");project.setProjectType("10");project.setManagerId(999L);
        when(projects.selectByIdForUpdate(10L)).thenReturn(project);
        stored=new ArrayList<>();when(members.selectProjectForUpdate(any())).thenAnswer(c->List.copyOf(stored));
        when(members.insertHistorical(any())).thenAnswer(c->{stored.add(c.<ProjectMemberHistoryMigrationMapper.HistoricalInsert>getArgument(0).values());return 1;});
        raw=new TreeMap<>();cn.iocoder.yudao.module.pms.project.api.migration.ProjectMemberSourceHistoryApi.COLUMNS.forEach(n->raw.put(n,null));
        raw.put("id","1");raw.put("projectId","101");raw.put("projectType","10");raw.put("memberRole","30");raw.put("memberCode","person");raw.put("memberName","原姓名");raw.put("email","");
        when(identities.getHistoricalMemberIdentities(anyLong(),any())).thenReturn(List.of());
        when(contacts.preview(any())).thenReturn(new FinalCustomerContactMigrationApi.Preview(new FinalCustomerContactMigrationApi.FinalCustomer(50L,10L,"客户","101"),"checksum",0));
        when(contacts.carry(any())).thenReturn(new FinalCustomerContactMigrationApi.Result(List.of(new FinalCustomerContactMigrationApi.Target("1",80L,"frozen"))));
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Command command(Entry e){return new Command(1L,2L,10L,List.of(e));}
    Entry fresh(){return new Entry("1",raw,null,null);}
    @Test void noAccountHistoryPreservesNullsAndNeverAuthorizesOrChangesPrimary() {
        var preview=service.preview(command(fresh()));assertTrue(stored.isEmpty());assertNull(preview.getFirst().targetId());
        var result=service.apply(command(fresh())).getFirst();var row=stored.getFirst();
        assertFalse(row.getAuthorizationConfirmed());assertNull(row.getUserId());assertNull(row.getAssignmentType());assertNull(row.getCreateTime());assertNull(row.getCreator());assertEquals("",row.getEmail());assertEquals(999L,project.getManagerId());
        assertEquals("UNCHANGED",service.apply(command(new Entry("1",raw,result.targetId(),result.formalFieldsJson()))).getFirst().action());assertEquals(1,stored.size());
        verify(members,never()).updateApprovedPrimary(any());verify(members,never()).closeApprovedInterval(any());
    }
    @Test void exactAccountCanBeLinkedWithoutGrantingAuthorization() {
        when(identities.getHistoricalMemberIdentities(anyLong(),any())).thenReturn(List.of(new SystemUserMigrationApi.MemberIdentity(44L,"person","E44")));
        service.apply(command(fresh()));assertEquals(44L,stored.getFirst().getUserId());assertFalse(stored.getFirst().getAuthorizationConfirmed());
    }
    @Test void role60UsesCustomerOwnerAndNoMemberAccountIsRequired() {
        raw.put("memberRole","60");raw.put("createTime","2020-01-01 00:00:00");
        assertNull(service.preview(command(fresh())).getFirst().targetId());verify(contacts).validateContacts(any());verify(contacts,never()).carry(any());
        var result=service.apply(command(fresh())).getFirst();assertEquals("cus_project_customer_contact_relation",result.targetTable());assertTrue(stored.isEmpty());
        service.apply(command(new Entry("1",raw,result.targetId(),result.formalFieldsJson())));verify(contacts).verify(any(),any());verify(contacts,times(1)).carry(any());
    }
    @Test void missingAuditAccountAndInvalidIntervalsCannotWrite() {
        raw.put("createBy","missing");assertThrows(IllegalArgumentException.class,()->service.apply(command(fresh())));raw.put("createBy",null);
        raw.put("effectiveFrom","2021-01-01 00:00:00");raw.put("effectiveTo","2020-01-01 00:00:00");assertThrows(IllegalArgumentException.class,()->service.apply(command(fresh())));
        verify(members,never()).insertHistorical(any());verify(contacts,never()).carry(any());
    }
    @Test void changedOrDeletedHistoricalTargetCannotBeOverwritten() {
        var result=service.apply(command(fresh())).getFirst();stored.getFirst().setMemberName("人工维护");
        assertThrows(IllegalArgumentException.class,()->service.apply(command(new Entry("1",raw,result.targetId(),result.formalFieldsJson()))));assertEquals(1,stored.size());
        stored.clear();assertThrows(IllegalArgumentException.class,()->service.apply(command(new Entry("1",raw,result.targetId(),result.formalFieldsJson()))));
    }
    @Test void unexpectedSideEffectCannotPassFullMemberSetVerification() {
        doAnswer(c->{var row=c.<ProjectMemberHistoryMigrationMapper.HistoricalInsert>getArgument(0).values();stored.add(row);
            var extra=new ProjectMemberAssignmentDO();extra.setId(999L);stored.add(extra);return 1;}).when(members).insertHistorical(any());
        assertThrows(IllegalArgumentException.class,()->service.apply(command(fresh())));
    }
    @Test void tenantAndExecutePermissionRemainRequired() {
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(2L,2L,10L,List.of(fresh()))));
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.apply(command(fresh())));assertTrue(stored.isEmpty());
    }
}
