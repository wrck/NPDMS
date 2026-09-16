package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectOperationCapabilityQueryServiceTest {
    public static class TestOwner { public void confirm() { } }
    private static final String OP = "SOL.SITE_SURVEY.CONFIRM";

    @Test void noViewStillReturnsIndependentNodeAndActionResults() {
        var owners = owner();
        when(owners.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(OP), "owner:1"));
        var service = service(owners, null, true);
        var result = service.inspect(1L, "STAGE", 2L, "3", null);
        assertNotNull(result.node()); assertEquals("UNAVAILABLE", result.presentation().status());
        assertTrue(result.actions().getFirst().allowed()); assertEquals("owner:1", result.ownerFactVersion());
    }
    @Test void ownerFailureDoesNotHideAuthorizedNodeSummary() {
        var owners = owner();
        when(owners.inspect(any())).thenThrow(new IllegalStateException("private payload must not leak"));
        var result = service(owners, null, true).inspect(1L, "STAGE", 2L, "3", null);
        assertNotNull(result.node()); assertFalse(result.actions().getFirst().allowed());
        assertEquals("OWNER_OPERATION_UNAVAILABLE_OR_FORBIDDEN", result.reason());
        assertFalse(JsonUtils.toJsonString(result).contains("private payload"));
    }
    @Test void staleExecutionDoesNotCallOwnerOrReturnActions() {
        var owners = owner();
        var result = service(owners, "EXECUTION_VERSION_CONFLICT", false).inspect(1L, "STAGE", 2L, "3", null);
        assertTrue(result.actions().isEmpty()); assertEquals("EXECUTION_VERSION_CONFLICT", result.reason());
        verify(owners, never()).inspect(any());
    }
    @Test void emptyOwnerPermissionsDoNotBecomeProjectGrants() {
        var owners = owner();
        when(owners.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(), null));
        var result = service(owners, null, true).inspect(1L, "STAGE", 2L, "3", null);
        assertFalse(result.actions().getFirst().allowed()); assertTrue(result.actions().getFirst().executionPermitted());
    }

    private ProjectBusinessOperationAccessProvider owner() {
        var provider = mock(ProjectBusinessOperationAccessProvider.class);
        when(provider.ownerContext()).thenReturn("SOL"); when(provider.objectType()).thenReturn("SITE_SURVEY");
        return provider;
    }
    private ProjectOperationCapabilityQueryService service(ProjectBusinessOperationAccessProvider owner, String reason, boolean granted) {
        var context = mock(ProjectOperationContextResolver.class);
        var project = new ProjectMasterDO(); project.setId(1L); project.setTenantId(1L);
        var round = new ProjectNodeExecutionDO(); round.setId(10L); round.setPlanVersionId(20L);
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setTargetContextCode("SOL"); binding.setTargetObjectType("SITE_SURVEY");
        binding.setOperationContract(JsonUtils.parseTree("{\"version\":1,\"operations\":[{\"operationCode\":\"" + OP
                + "\",\"operationVersion\":1,\"pre\":{\"mode\":\"NONE\"},\"post\":{\"mode\":\"NONE\"}}],\"programs\":{}}"));
        when(context.resolve(eq(1L), eq("STAGE"), eq(2L), isNull())).thenReturn(new ProjectOperationContextResolver.Context(
                1L, 4L, project, new ProjectOperationCapabilities.Node(1L, "STAGE", 2L, "P", "准备", "ACTIVE"),
                round, binding, null, granted, reason));
        ProjectBusinessOperationProvider descriptors = () -> List.of(new ProjectBusinessOperationDescriptor(OP, 1,
                "SOL", "SITE_SURVEY", "确认", "CONFIRM", Set.of("PRE", "POST"), TestOwner.class, "confirm"));
        var registry = new ProjectBusinessOperationRegistry(List.of(descriptors), List.of((code, version) -> OP.equals(code) && version == 1));
        var evaluator = new ProjectOperationRuleEvaluator(mock(ProjectRuleEvaluationService.class), mock(ProjectDecisionTableService.class));
        return new ProjectOperationCapabilityQueryService(context, registry, List.of(owner), evaluator, mock(BusinessViewQueryApi.class));
    }
}
