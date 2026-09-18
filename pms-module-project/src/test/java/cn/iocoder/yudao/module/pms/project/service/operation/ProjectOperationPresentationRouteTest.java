package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.businessview.AcceptanceBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplatePresentationRoutes;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectOperationPresentationRouteTest {
    static final String URL = "/pms/project/acceptance-report";

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "DISABLED", "MISSING", "WRONG_VERSION", "UNKNOWN_ROUTE", "NO_REGISTRY"})
    void presentationDoesNotAuthorizeACommandOrFallBackWhenUnavailable(String condition) {
        var permissions = mock(PermissionApi.class);
        var provider = new AcceptanceBusinessViewProvider(permissions);
        var binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setTargetContextCode("ACC"); binding.setTargetObjectType("ACCEPTANCE"); binding.setComponentKey("ACC_ACCEPTANCE_REPORT");
        var frozen = view("PUBLISHED", "1");
        binding.setBusinessViewSnapshot(JsonUtils.parseTree(JsonUtils.toJsonString(frozen)));
        var page = new TemplateExecutionConfiguration.Presentation(condition.equals("UNKNOWN_ROUTE") ? "/pms/unknown" : URL,
                Map.of("projectId", "$project.id", "objectId", "$object.id"));
        var contexts = mock(ProjectOperationContextResolver.class);
        var project = new ProjectMasterDO(); project.setId(9007199254740993L);
        when(contexts.resolve(anyLong(), anyString(), anyLong(), isNull())).thenReturn(new ProjectOperationContextResolver.Context(
                1L, 4L, project, new ProjectOperationCapabilities.Node(project.getId(), "TASK", 2L, "T", "报告", "ACTIVE"),
                null, binding, null, false, null, page));
        var views = mock(BusinessViewQueryApi.class);
        when(views.getRevision(any())).thenReturn(condition.equals("MISSING") ? null
                : view(condition.equals("DISABLED") ? "DISABLED" : "PUBLISHED", condition.equals("WRONG_VERSION") ? "2" : "1"));
        var operations = mock(ProjectBusinessOperationRegistry.class);
        var evaluator = mock(ProjectOperationRuleEvaluator.class);
        var service = new ProjectOperationCapabilityQueryService(contexts, operations, List.of(), evaluator, views);
        if (!condition.equals("NO_REGISTRY")) ReflectionTestUtils.setField(service, "presentationRoutes", new TemplatePresentationRoutes(List.of(provider)));
        var result = service.inspect(project.getId(), "TASK", 2L, "9007199254740994", null);
        assertEquals("LEGACY_BINDING", result.reason()); assertTrue(result.actions().isEmpty());
        assertEquals(page.pageUrl(), result.presentation().pageUrl());
        if (Set.of("PUBLISHED", "DISABLED").contains(condition)) {
            assertEquals(condition.equals("PUBLISHED") ? "AVAILABLE" : "READ_ONLY", result.presentation().status());
            assertEquals(Map.of("projectId", "9007199254740993", "objectId", "9007199254740994"), result.presentation().query());
        } else {
            assertEquals("UNAVAILABLE", result.presentation().status()); assertNull(result.presentation().query());
        }
        verifyNoInteractions(operations, evaluator, permissions);
    }

    @Test
    void oldObservationShapeHasNoNewFieldsAndNewCollectionsCannotBeMutated() {
        var legacy = JsonUtils.parseTree(JsonUtils.toJsonString(new ProjectOperationCapabilities.Presentation(null, "AVAILABLE", null)));
        assertFalse(legacy.has("pageUrl")); assertFalse(legacy.has("query"));
        var json = JsonUtils.parseTree("{\"id\":7}");
        var query = new java.util.LinkedHashMap<>(Map.of("projectId", "1"));
        var result = new ProjectOperationCapabilities.Presentation(json, "AVAILABLE", null, URL, query);
        query.put("projectId", "2");
        ((tools.jackson.databind.node.ObjectNode) json).put("id", 8);
        assertEquals("1", result.query().get("projectId")); assertEquals(7, result.registration().path("id").intValue());
        assertThrows(UnsupportedOperationException.class, () -> result.query().put("projectId", "3"));
    }

    private BusinessViewRevision view(String status, String version) {
        return new BusinessViewRevision(7L, "ACCEPTANCE", "REPORT", 1L, "ACC", BusinessViewComponentProvider.ViewSource.PAGE,
                "ACC_ACCEPTANCE_REPORT", version, null, JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"),
                "QUERY", "COMMAND", "PERMISSION", null, null, 1, status, Set.of());
    }
}
