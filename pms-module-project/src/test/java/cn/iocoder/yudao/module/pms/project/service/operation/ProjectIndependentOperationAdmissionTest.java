package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectOperationEntryPolicy;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessExecutionService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectIndependentOperationAdmissionTest {
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectOperationContextResolver contexts = mock(ProjectOperationContextResolver.class);
    private final ProjectNodeExecutionApi nodes = mock(ProjectNodeExecutionApi.class);
    private final ProjectBusinessExecutionService legacy = mock(ProjectBusinessExecutionService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectBusinessExecutionService> old = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectOperationContextResolver> contextProvider = mock(ObjectProvider.class);
    private final ProjectOperationEntryPolicy policies = new ProjectOperationEntryPolicy(List.of(provider(
            ProjectOperationControlScope.PROJECT_ENTRY_ONLY)));
    private final ProjectIndependentOperationAdmission admission = new ProjectIndependentOperationAdmission(policies, scopes, contextProvider);
    private final ProjectOperationAwareExecutionGuard guard = new ProjectOperationAwareExecutionGuard(old, nodes, admission);
    private final ProjectBusinessExecutionSelection selection = new ProjectBusinessExecutionSelection(null,
            new ProjectStageExecutionContext(9L,1,21L,1,22L,1,23L,24L,1,2,true));

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        var user = new LoginUser(); user.setId(8L); user.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        when(old.getObject()).thenReturn(legacy);
        when(contextProvider.getObject()).thenReturn(contexts);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 3L, Set.of(9L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L, 3L, Set.of(9L), Set.of()));
    }
    @AfterEach void cleanUp() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void ordinaryWriteUsesScopeLocksWithoutConsultingPlansNodesOrViews() {
        guard.lockForWrite(request(null));
        verify(scopes).resolveCurrent(new ProjectCurrentScopeQuery(7L,8L,9L,ProjectScopeApi.ACTION_MANAGE));
        verify(scopes).lockAndRevalidate(new ProjectScopeRevalidationQuery(7L,8L,9L,ProjectScopeApi.ACTION_MANAGE,3L));
        verifyNoInteractions(old, legacy, nodes, contexts);
    }

    @Test void explicitProjectEntryStillRejectsMissingOrUnwritableExecution() {
        when(contexts.resolve(9L,"STAGE",21L,selection)).thenReturn(new ProjectOperationContextResolver.Context(
                7L,8L,null,null,null,new TemplateExecutionSnapshot.BindingContract(),selection,true,null));
        doThrow(new IllegalStateException("NO_ACTIVE_NODE")).when(legacy).lockForWrite(any());
        assertThrows(IllegalStateException.class, () -> guard.lockForWrite(request(selection)));
        verify(legacy).lockForWrite(request(selection));
        verifyNoInteractions(scopes, nodes);
    }

    @Test void undeclaredVersionOwnerAndEntityDoNotInheritIndependentPolicy() {
        for (var request : List.of(new WriteRequest(9L,"SOL","SITE_SURVEY",null),
                new WriteRequest(9L,"OTHER","SITE_SURVEY",null,"SOL.SITE_SURVEY.CONFIRM",1,"11"),
                new WriteRequest(9L,"SOL","OTHER",null,"SOL.SITE_SURVEY.CONFIRM",1,"11"),
                new WriteRequest(9L,"SOL","SITE_SURVEY",null,"SOL.SITE_SURVEY.CONFIRM",2,"11"))) {
            guard.lockForWrite(request);
            verify(legacy).lockForWrite(request);
        }
        verifyNoInteractions(scopes, nodes);
    }

    @ParameterizedTest @ValueSource(strings={"missing", "placeholder", "no-version", "revoked", "lock-error", "anonymous"})
    void unavailableAuthorizationNeverFallsBackToLegacy(String damage) {
        switch (damage) {
            case "missing" -> when(scopes.resolveCurrent(any())).thenReturn(null);
            case "placeholder" -> when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,3L,Set.of(),Set.of(9L)));
            case "no-version" -> when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,null,Set.of(9L),Set.of()));
            case "revoked" -> when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L,3L,Set.of(),Set.of()));
            case "lock-error" -> when(scopes.lockAndRevalidate(any())).thenThrow(new IllegalStateException("SCOPE_CHANGED"));
            case "anonymous" -> SecurityContextHolder.clearContext();
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, () -> guard.lockForWrite(request(null)));
        verifyNoInteractions(old, legacy, nodes);
    }

    @Test void allEntriesCannotInventAnActiveNodeWhenContextIsMissing() {
        var strict = new ProjectIndependentOperationAdmission(new ProjectOperationEntryPolicy(List.of(
                provider(ProjectOperationControlScope.ALL_ENTRIES))), scopes, contextProvider);
        assertThrows(ServiceException.class, () -> strict.admit(request(null)));
        assertThrows(ServiceException.class, () -> strict.admit(request(selection)));
        verifyNoInteractions(scopes, contexts);
    }

    @Test void verifiedEntryCannotRemoveSelectionOrChangeSubjectToBecomeIndependent() {
        try (var ignored = ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(
                7L,8L,9L,"SOL","SITE_SURVEY","SOL.SITE_SURVEY.CONFIRM",1,"11",selection))) {
            guard.lockForWrite(request(selection));
            verify(nodes).lockAndRevalidateStage(selection.stage());
            assertThrows(IllegalStateException.class, () -> guard.lockForWrite(request(null)));
            assertThrows(IllegalStateException.class, () -> guard.lockForWrite(new WriteRequest(
                    9L,"SOL","SITE_SURVEY",selection,"SOL.SITE_SURVEY.CONFIRM",1,"12")));
            verifyNoInteractions(scopes, old, legacy);
        }
    }

    @Test void selectedNewContractCannotBypassPrePostThroughAnOrdinaryOwnerEndpoint() {
        var binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setOperationContract(JsonUtils.parseTree("{}"));
        when(contexts.resolve(9L,"STAGE",21L,selection)).thenReturn(new ProjectOperationContextResolver.Context(
                7L,8L,null,null,null,binding,selection,true,null));
        assertThrows(ServiceException.class, () -> guard.lockForWrite(request(selection)));
        verifyNoInteractions(old,legacy,nodes,scopes);
    }

    @Test void unavailableSelectedContextDoesNotFallBackToIndependentOrLegacy() {
        assertThrows(ServiceException.class, () -> guard.lockForWrite(request(selection)));
        assertThrows(ServiceException.class, () -> guard.lockForWrite(request(new ProjectBusinessExecutionSelection(null,null))));
        verifyNoInteractions(old,legacy,nodes,scopes);
    }

    private WriteRequest request(ProjectBusinessExecutionSelection execution) {
        return new WriteRequest(9L,"SOL","SITE_SURVEY",execution,"SOL.SITE_SURVEY.CONFIRM",1,"11");
    }
    private static ProjectBusinessOperationProvider provider(ProjectOperationControlScope scope) {
        return new ProjectBusinessOperationProvider() {
            public List<ProjectBusinessOperationDescriptor> operations() {
                return List.of(new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.CONFIRM",1,"SOL","SITE_SURVEY",
                        "确认工勘","CONFIRM",Set.of("PRE","POST"),Object.class,"toString"));
            }
            public Map<String,ProjectOperationControlScope> controlScopes() { return Map.of("SOL.SITE_SURVEY.CONFIRM",scope); }
        };
    }
}
