package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurationChangeBpmAuthorizationGuardTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanChangeMapper changeMapper;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;

    private DurationChangeBpmAuthorizationGuard guard;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        login(10L);
        guard = new DurationChangeBpmAuthorizationGuard(planMapper, changeMapper, permissionApi,
                projectScopeApi, participantFactApi);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthorizeFrozenCurrentServiceManagerForApprove() {
        stubRows(9L, 10L);
        when(permissionApi.hasAnyPermissions(10L,
                DurationChangeBpmAuthorizationGuard.PERMISSION_APPROVE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(301L, 1L, Set.of(301L), Set.of()));
        ProjectParticipantFact fact = fact(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
        when(participantFactApi.inspect(any())).thenReturn(fact);
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(fact);

        var result = guard.authorize("P-1", DurationChangeBpmResultService.TerminalResult.APPROVE);

        assertEquals(301L, result.projectId());
        verify(participantFactApi).lockAndRevalidate(eq(new ProjectParticipantFactRevalidationQuery(
                301L, 10L, 3, "ACTIVE", null, Set.of(
                ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1,
                ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L2))));
    }

    @Test
    void shouldUseApplicantManagerBoundaryForCancel() {
        stubRows(10L, 11L);
        when(permissionApi.hasAnyPermissions(10L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(301L, 1L, Set.of(301L), Set.of()));
        ProjectParticipantFact fact = fact(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        when(participantFactApi.inspect(any())).thenReturn(fact);
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(fact);

        guard.authorize("P-1", DurationChangeBpmResultService.TerminalResult.CANCEL);

        verify(participantFactApi).lockAndRevalidate(eq(new ProjectParticipantFactRevalidationQuery(
                301L, 10L, 3, "ACTIVE", null,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER))));
    }

    @Test
    void shouldRejectActorWhoIsNotFrozenApprover() {
        stubRows(9L, 11L);

        assertThrows(ServiceException.class, () -> guard.authorize(
                "P-1", DurationChangeBpmResultService.TerminalResult.REJECT));
    }

    private void stubRows(Long applicantId, Long approverId) {
        ConstructionPlanChangeDO change = new ConstructionPlanChangeDO();
        change.setId(801L);
        change.setTenantId(0L);
        change.setPlanId(501L);
        change.setApplicantUserId(applicantId);
        change.setApproverUserId(approverId);
        when(changeMapper.selectByProcessInstanceId(any())).thenReturn(change);
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setId(501L);
        plan.setTenantId(0L);
        plan.setProjectId(301L);
        when(planMapper.selectById(any())).thenReturn(plan);
    }

    private ProjectParticipantFact fact(String role) {
        return new ProjectParticipantFact(301L, 10L, Set.of(role), "PRIMARY",
                "ACTIVE", "S2", 3, 3L);
    }

    private void login(Long actorId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(actorId);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

}
