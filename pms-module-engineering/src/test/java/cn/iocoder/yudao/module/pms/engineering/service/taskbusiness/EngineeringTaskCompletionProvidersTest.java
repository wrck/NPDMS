package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalTaskFactMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingTaskFactMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineeringTaskCompletionProvidersTest {
    final ArrivalTaskFactMapper arrivals = mock(ArrivalTaskFactMapper.class);
    final TrainingTaskFactMapper trainings = mock(TrainingTaskFactMapper.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    final ArrivalTaskBusinessObjectProvider arrival = new ArrivalTaskBusinessObjectProvider(arrivals, scopes, permissions, executions);
    final TrainingTaskBusinessObjectProvider training = new TrainingTaskBusinessObjectProvider(trainings, scopes, permissions, executions);
    final Context context = new Context(1L, 3L, 9L, 20L, "test");
    final ProjectTaskExecutionContext execution = new ProjectTaskExecutionContext(9L, 1L, 20L, 1, 21L, 1, 22L, 23L, 1, 1, 24L, 1, true, null);
    final ArrivalDO receipt = new ArrivalDO();
    final TrainingDO session = new TrainingDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var login = new LoginUser(); login.setId(3L); login.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(eq(3L), any(String.class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);
        receipt.setId(42L); receipt.setTenantId(1L); receipt.setProjectId(9L); receipt.setCode("arrival"); receipt.setVersion(4L);
        session.setId(43L); session.setTenantId(1L); session.setProjectId(9L); session.setName("training"); session.setVersion(5L);
        when(arrivals.selectTaskFact(any())).thenReturn(receipt);
        when(arrivals.selectTaskFactForUpdate(any())).thenReturn(receipt);
        when(trainings.selectTaskFact(any())).thenReturn(session);
        when(trainings.selectTaskFactForUpdate(any())).thenReturn(session);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void onlyFormalSignedReceiptAndCustomerConfirmationComplete() {
        for (int status = 0; status <= 2; status++) {
            receipt.setStatus(status);
            assertEquals(status == 1, arrival.inspect(context, "42").completionFacts().get("ARRIVAL_SIGNED"));
            assertEquals(status == 1, arrival.lockCompletionFact(new CompletionContext(1L, execution), "42").handlingCompleted());
        }
        for (int status = 0; status <= 3; status++) {
            session.setStatus(status);
            assertEquals(status == 2, training.inspect(context, "43").completionFacts().get("TRAINING_CONFIRMED"));
            assertEquals(status == 2, training.lockCompletionFact(new CompletionContext(1L, execution), "43").handlingCompleted());
        }
        assertTrue(training.inspect(context, "43").artifacts().isEmpty());
    }

    @Test void wrongProjectDeletedAndStaleOwnerVersionCannotBecomeTaskEvidence() {
        receipt.setStatus(1); session.setStatus(2);
        String version = arrival.inspect(context, "42").factVersion(); receipt.setVersion(5L);
        assertThrows(IllegalArgumentException.class, () -> arrival.lockAndRevalidate(context, "42", version));
        receipt.setProjectId(10L);
        assertThrows(IllegalArgumentException.class, () -> arrival.lockCompletionFact(new CompletionContext(1L, execution), "42"));
        session.setDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> training.lockCompletionFact(new CompletionContext(1L, execution), "43"));
        session.setDeleted(false); session.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> training.inspect(context, "43"));
    }

    @Test void emptyScopeAndForgedTenantDenyBeforeReadingTheOwner() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of(9L)));
        assertThrows(SecurityException.class, () -> arrival.inspect(context, "42"));
        assertThrows(SecurityException.class, () -> training.inspect(new Context(2L,3L,9L,20L,"forged"), "43"));
        verifyNoInteractions(arrivals, trainings);
    }

    @Test void unattendedFactsRequireTheCurrentExecutionButNoSyntheticLogin() {
        SecurityContextHolder.clearContext(); session.setStatus(2);
        assertTrue(training.lockCompletionFact(new CompletionContext(1L, execution), "43").handlingCompleted());
        when(executions.lockAndRevalidate(execution)).thenThrow(new IllegalArgumentException("OLD_EXECUTION"));
        assertThrows(IllegalArgumentException.class, () -> training.lockCompletionFact(new CompletionContext(1L, execution), "43"));
    }
}
