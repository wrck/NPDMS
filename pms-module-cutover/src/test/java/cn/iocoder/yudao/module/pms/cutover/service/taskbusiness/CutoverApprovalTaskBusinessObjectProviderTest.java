package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.completion.CutoverCompletionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverApprovalTaskBusinessObjectProviderTest {
    final CutoverCompletionMapper tasks=mock(CutoverCompletionMapper.class);
    final CutoverPlanRevisionMapper plans=mock(CutoverPlanRevisionMapper.class);
    final CutoverApprovalFactApi approvals=mock(CutoverApprovalFactApi.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final CutoverApprovalTaskBusinessObjectProvider provider=new CutoverApprovalTaskBusinessObjectProvider(
            tasks,plans,approvals,scopes,permissions,mock(ProjectNodeExecutionApi.class));
    final Context context=new Context(1L,3L,9L,20L,"test");
    final CutoverTaskDO task=new CutoverTaskDO();
    final CutoverPlanRevisionDO plan=new CutoverPlanRevisionDO();
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);var login=new LoginUser();login.setId(3L);login.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(login,new MockHttpServletRequest());
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        when(permissions.hasAnyPermissions(3L,"pms:cutover-task:query")).thenReturn(true);
        task.setId(42L);task.setTenantId(1L);task.setProjectId(9L);task.setVersion(4L);task.setTaskName("割接");
        plan.setId(51L);plan.setTenantId(1L);plan.setCutoverTaskId(42L);plan.setVersion(2L);plan.setCurrentMarker(1);plan.setStatusCode("SUBMITTED");
        when(tasks.one(any())).thenReturn(task);when(plans.selectCurrent(any())).thenReturn(plan);when(plans.selectCurrentForUpdate(any())).thenReturn(plan);
    }
    @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void onlyCurrentFormalApprovalCountsWithoutRequiringP6Archive(){
        for(var status:ApprovalStatus.values()){
            observe(fact(status,null,100L));
            assertEquals(status==ApprovalStatus.APPROVED,provider.inspect(context,"42").completionFacts().get("CUTOVER_APPROVED"));
        }
        observe(fact(ApprovalStatus.APPROVED,99L,100L));assertFalse(completed());
        observe(fact(ApprovalStatus.APPROVED,null,null));assertFalse(completed());
        observe(fact(ApprovalStatus.APPROVED,null,100L));plan.setStatusCode("DRAFT");assertFalse(completed());
        plan.setStatusCode("SUBMITTED");plan.setCurrentMarker(null);assertFalse(completed());
    }
    @Test void oldPlanWrongProjectAndStaleLockedApprovalAreRejected(){
        var fact=fact(ApprovalStatus.APPROVED,null,100L);observe(fact);
        String version=provider.inspect(context,"42").factVersion();
        when(approvals.lockAndRevalidate(any())).thenReturn(new CutoverApprovalRevalidationResult(RevalidationStatus.VALID,fact));
        assertTrue(provider.lockAndRevalidate(context,"42",version).completionFacts().get("CUTOVER_APPROVED"));
        plan.setVersion(3L);assertThrows(IllegalArgumentException.class,()->provider.lockAndRevalidate(context,"42",version));
        plan.setCutoverTaskId(44L);assertThrows(IllegalArgumentException.class,()->provider.inspect(context,"42"));
        plan.setCutoverTaskId(42L);task.setProjectId(10L);assertThrows(IllegalArgumentException.class,()->provider.inspect(context,"42"));
    }
    @Test void missingApprovalAndEmptyProjectScopeNeverPass(){
        when(approvals.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.NOT_FOUND,null));assertFalse(completed());
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of(9L)));
        assertThrows(SecurityException.class,()->provider.inspect(context,"42"));
        assertThrows(SecurityException.class,()->provider.inspect(new Context(2L,3L,9L,20L,"spoof"),"42"));
    }
    private boolean completed(){return provider.inspect(context,"42").completionFacts().get("CUTOVER_APPROVED");}
    private void observe(CutoverApprovalFact fact){when(approvals.inspect(any())).thenReturn(new CutoverApprovalInspectResult(InspectStatus.FOUND,fact));}
    private CutoverApprovalFact fact(ApprovalStatus status,Long replacement,Long time){return new CutoverApprovalFact(61L,2L,42L,51L,1,status,1,replacement,time,status==ApprovalStatus.REJECTED?"拒绝":null);}
}
