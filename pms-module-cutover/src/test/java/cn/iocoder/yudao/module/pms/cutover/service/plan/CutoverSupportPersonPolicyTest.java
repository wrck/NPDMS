package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberSelectionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CutoverSupportPersonPolicyTest {
    final ProjectMemberSelectionApi members=mock(ProjectMemberSelectionApi.class);
    final RoleCandidateFactApi roles=mock(RoleCandidateFactApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final CutoverSupportPersonPolicy policy=new CutoverSupportPersonPolicy(members,roles,users);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    CutoverSupportPersonPolicy.Query query(String role){return new CutoverSupportPersonPolicy.Query(1L,2L,30L,role);}
    CutoverSupportPersonPolicy.Validation validation(String role, CutoverSupportPersonPolicy.Reference reference, String name){
        return new CutoverSupportPersonPolicy.Validation(query(role),reference,name,true);
    }
    @Test void listsProjectMembersRatherThanGlobalContactsAndKeepsTheirVersions(){
        when(members.page(ProjectMemberSelectionApi.Query.builder().tenantId(1L).actorId(2L).projectId(30L).pageNo(1).pageSize(100).role("END_USER").build())).thenReturn(
            new ProjectMemberSelectionApi.Page(List.of(new ProjectMemberSelectionApi.Member(70L,3L,"项目成员","13800000001")),1L));
        var result=policy.choices(query("CUSTOMER"));
        assertEquals(new CutoverSupportPersonPolicy.Reference("PROJECT_MEMBER",70L,3L),result.getFirst().reference());
        verifyNoInteractions(roles,users);
    }
    @Test void locksActualProjectManagerMembershipAndRejectsMissingRoleStaleVersionAndForgedName(){
        var ref=new CutoverSupportPersonPolicy.Reference("SYSTEM_USER",7L,9L);
        when(roles.lockAndInspectActor(any())).thenReturn(List.of());
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_FIRST_LINE",ref,"工程师")));
        when(roles.lockAndInspectActor(any())).thenReturn(List.of(new RoleCandidateFactApi.ActorMembership(7L,8L,"PROJECT_MANAGER",1L,9L)));
        AdminUserRespDTO user=new AdminUserRespDTO(); user.setId(7L); user.setNickname("工程师"); user.setStatus(0);
        when(users.getUser(7L)).thenReturn(user);
        assertDoesNotThrow(()->policy.validate(validation("DP_FIRST_LINE",ref,"工程师")));
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_RND",ref,"工程师")));
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_FIRST_LINE",new CutoverSupportPersonPolicy.Reference("SYSTEM_USER",7L,8L),"工程师")));
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_FIRST_LINE",ref,"伪造姓名")));
        user.setStatus(1);
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_FIRST_LINE",ref,"工程师")));
    }
    @Test void requiresStaffSelectionButAllowsManualCustomersAndRejectsCrossSource(){
        assertDoesNotThrow(()->policy.validate(validation("CUSTOMER",null,"手填客户")));
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_FIRST_LINE",null,"手填工程师")));
        assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("DP_FIRST_LINE",new CutoverSupportPersonPolicy.Reference("PROJECT_MEMBER",7L,0L),"工程师")));
        when(members.lockAndInspect(ProjectMemberSelectionApi.Revalidation.builder().tenantId(1L).actorId(2L).projectId(30L).assignmentId(7L).expectedVersion(0L).role("END_USER").build())).thenThrow(new SecurityException("其他项目"));
        assertThrows(SecurityException.class,()->policy.validate(validation("CUSTOMER",new CutoverSupportPersonPolicy.Reference("PROJECT_MEMBER",7L,0L),"客户")));
        when(members.lockAndInspect(any())).thenThrow(new IllegalArgumentException("项目成员已失效，请重新选择"));
        var stale=assertThrows(CutoverPlanApplicationException.class,()->policy.validate(validation("CUSTOMER",new CutoverSupportPersonPolicy.Reference("PROJECT_MEMBER",7L,0L),"客户")));
        assertEquals(CutoverPlanApplicationException.Code.SUPPORT_ARRANGEMENT_INCOMPLETE,stale.code());
    }
    @Test void rejectsWrongTenantBeforeReadingAnyPeople(){
        assertThrows(SecurityException.class,()->policy.choices(new CutoverSupportPersonPolicy.Query(2L,2L,30L,"DP_FIRST_LINE")));
        verifyNoInteractions(members,roles,users);
    }
}
