package cn.iocoder.yudao.module.pms.project.service.taskworkbench;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TaskExecutionPolicyTest {
    @Test void onlyUnassignedPendingTasksReuseTheFrozenStartDefinition() {
        assertEquals("PENDING_START", TaskExecutionPolicy.transitionSource("PENDING_ASSIGN", "START", false));
        assertEquals("PENDING_ASSIGN", TaskExecutionPolicy.transitionSource("PENDING_ASSIGN", "START", true));
        for (String action : List.of("ASSIGN", "SUBMIT", "COMPLETE", "CANCEL"))
            assertEquals("PENDING_ASSIGN", TaskExecutionPolicy.transitionSource("PENDING_ASSIGN", action, false));
        for (String status : List.of("PENDING_START", "IN_PROGRESS", "PENDING_ACCEPT", "DONE", "CLOSED"))
            assertEquals(status, TaskExecutionPolicy.transitionSource(status, "START", false));
    }
    private ProjectMemberAssignmentDO member(String role, Long project) {
        var row = new ProjectMemberAssignmentDO(); row.setProjectId(project); row.setMemberRole(role); row.setAssignmentType("COLLABORATOR"); return row;
    }
    @Test void allThreeEffectiveMemberRolesCanExecuteWithoutDesignationOrPrimaryStatus() {
        for (String role : List.of("PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2","TEAM_MEMBER"))
            assertTrue(TaskExecutionPolicy.permits(10L,1L,null,List.of(member(role,10L))));
    }
    @Test void anExplicitDesignationRestrictsManagersAndMembersToThatPerson() {
        var designated = new ProjectTaskAssignmentDO(); designated.setAssigneeUserId(2L);
        assertFalse(TaskExecutionPolicy.permits(10L,1L,designated,List.of(member("PROJECT_MANAGER",10L))));
        assertTrue(TaskExecutionPolicy.permits(10L,2L,designated,List.of(member("TEAM_MEMBER",10L))));
    }
    @Test void noDefaultPermissionForOutsidersOtherProjectsOrUnlistedRoles() {
        assertFalse(TaskExecutionPolicy.permits(10L,1L,null,List.of()));
        assertFalse(TaskExecutionPolicy.permits(10L,1L,null,List.of(member("TEAM_MEMBER",11L))));
        assertFalse(TaskExecutionPolicy.permits(10L,1L,null,List.of(member("SALES_REPRESENTATIVE",10L))));
    }
}
