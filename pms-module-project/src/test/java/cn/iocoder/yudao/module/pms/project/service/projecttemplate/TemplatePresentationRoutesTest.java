package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplatePresentationRoutesTest {
    static final String URL = "/pms/delivery-business/site-survey";
    static final String VIEW = """
            {"id":7,"entityType":"SITE_SURVEY","viewKey":"SURVEY","revisionNo":1,"ownerContext":"SOL",
             "viewSource":"PAGE","componentKey":"SOL_SITE_SURVEY","componentVersion":"1","contextSchema":{},
             "supportedActions":["QUERY"],"allowedActions":[],"queryProviderKey":"QUERY","commandProviderKey":"COMMAND",
             "permissionProviderKey":"PERMISSION","version":1,"status":"PUBLISHED"}
            """;

    static BusinessViewComponentProvider provider(String path) {
        var provider = mock(BusinessViewComponentProvider.class);
        when(provider.pagePaths()).thenReturn(Set.of(path));
        when(provider.component()).thenReturn(new BusinessViewComponentProvider.Component("SITE_SURVEY", "SOL",
                BusinessViewComponentProvider.ViewSource.PAGE, "SOL_SITE_SURVEY", "1", JsonUtils.parseTree("{}"),
                JsonUtils.parseTree("[]"), "QUERY", "COMMAND", "PERMISSION", "工勘"));
        return provider;
    }

    static TemplateDesignerDocument source(boolean operation) {
        var source = TemplateExecutionConfigurationCompilationTest.source();
        var task = source.getTasks().getFirst();
        task.getWorkBinding().setComponentKey("SOL_SITE_SURVEY");
        task.getWorkBinding().setBusinessViewSnapshot(JsonUtils.parseTree(VIEW));
        var execution = operation ? (ObjectNode) task.getExecution() : JsonUtils.parseObject("{}", ObjectNode.class);
        execution.set("presentation", JsonUtils.parseTree("""
                {"pageUrl":"/pms/delivery-business/site-survey","query":{"projectId":"$project.id","objectId":"$object.id"}}
                """));
        task.setExecution(execution);
        return source;
    }

    static TemplateCompiler compiler() {
        var compiler = TemplateExecutionConfigurationCompilationTest.compiler("OK");
        Object configurations = ReflectionTestUtils.getField(compiler, "executionConfigurations");
        ReflectionTestUtils.setField(configurations, "presentationRoutes", new TemplatePresentationRoutes(List.of(provider(URL))));
        return compiler;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pageOnlyAndPageWithOperationFreezeAndRoundTripWithoutReinterpreting(boolean operation) {
        var source = source(operation);
        String before = JsonUtils.toJsonString(source);
        var result = compiler().compileVersioned(source);
        assertTrue(result.valid(), () -> result.issues().toString());
        assertNull(result.snapshotHash());
        assertEquals(before, JsonUtils.toJsonString(source));
        assertEquals(result.snapshot(), TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(result.snapshot())));
        assertEquals(URL, result.snapshot().getTasks().getFirst().getExecution().path("presentation").path("pageUrl").asText());
        if (!operation) assertNull(result.snapshot().getTasks().getFirst().getBinding().getOperationContract());
    }

    @Test
    void stageCanDisplayTheSameOwnerPageWithoutGeneratingATask() {
        var source = source(false);
        var task = source.getTasks().getFirst();
        var stage = source.getStages().getFirst();
        stage.setWorkBinding(task.getWorkBinding());
        stage.setExecution(task.getExecution());
        stage.setCompletionRule(task.getCompletionRule());
        task.setExecution(null);
        int before = source.getTasks().size();
        var result = compiler().compileVersioned(source);
        assertTrue(result.valid(), () -> result.issues().toString());
        assertEquals(before, result.snapshot().getTasks().size());
        assertNotNull(result.snapshot().getStages().getFirst().getExecution());
        assertEquals(result.snapshot(), TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(result.snapshot())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_ROUTE", "OWNER", "ENTITY", "COMPONENT", "VERSION", "DYNAMIC_FORM", "NO_BINDING",
            "NO_VIEW", "NO_VIEW_ID", "COERCED_COMPONENT_VERSION", "EXTRA_PARAMETER", "LITERAL_PROJECT", "SWAPPED_REFERENCE"})
    void invalidPageNeverProducesASnapshot(String damage) {
        var source = source(false);
        var task = source.getTasks().getFirst();
        var binding = task.getWorkBinding();
        var page = (ObjectNode) task.getExecution().path("presentation");
        var query = (ObjectNode) page.path("query");
        var view = (ObjectNode) binding.getBusinessViewSnapshot();
        switch (damage) {
            case "UNKNOWN_ROUTE" -> page.put("pageUrl", "/pms/not-deployed");
            case "OWNER" -> view.put("ownerContext", "ACC");
            case "ENTITY" -> view.put("entityType", "OTHER");
            case "COMPONENT" -> view.put("componentKey", "OTHER");
            case "VERSION" -> view.put("componentVersion", "2");
            case "DYNAMIC_FORM" -> view.put("viewSource", "DYNAMIC_FORM");
            case "NO_BINDING" -> task.setWorkBinding(null);
            case "NO_VIEW" -> binding.setBusinessViewSnapshot(null);
            case "NO_VIEW_ID" -> view.remove("id");
            case "COERCED_COMPONENT_VERSION" -> view.put("componentVersion", 1);
            case "EXTRA_PARAMETER" -> query.put("name", "合法但页面不支持");
            case "LITERAL_PROJECT" -> query.put("projectId", "123");
            case "SWAPPED_REFERENCE" -> query.put("projectId", "$object.id");
            default -> throw new AssertionError(damage);
        }
        String before = JsonUtils.toJsonString(source);
        var result = compiler().compileVersioned(source);
        assertFalse(result.valid()); assertNull(result.snapshot());
        assertEquals(before, JsonUtils.toJsonString(source));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.field().contains("presentation")));
    }

    @Test
    void readerUsesOnlyFrozenContentAndRejectsMalformedParameterMeaning() {
        var snapshot = compiler().compileVersioned(source(false)).snapshot();
        var frozen = JsonUtils.toJsonString(snapshot);
        assertEquals(snapshot, TemplateExecutionSnapshotReader.read(frozen));
        var query = (ObjectNode) snapshot.getTasks().getFirst().getExecution().path("presentation").path("query");
        query.put("objectId", "$actor.id");
        assertThrows(RuntimeException.class, () -> TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(snapshot)));
    }

    @Test
    void registryIsUniqueSideEffectFreeAndKeepsLegacyProvidersCompatible() {
        var provider = provider(URL);
        var legacy = mock(BusinessViewComponentProvider.class, CALLS_REAL_METHODS);
        var registry = new TemplatePresentationRoutes(List.of(legacy, provider));
        var source = source(false).getTasks().getFirst();
        var page = TemplateExecutionConfiguration.read(source.getExecution()).presentation();
        registry.validate(page, source.getWorkBinding());
        registry.validate(page, source.getWorkBinding());
        verify(provider, times(1)).component();
        verify(provider, never()).canConfigure(any(), any());
        verify(provider, never()).validateConfiguration(any(), any(), any());
        verify(legacy, never()).component();
        assertThrows(IllegalArgumentException.class, () -> new TemplatePresentationRoutes(List.of(provider, provider(URL))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"//remote", "https://remote", "/a/../b", "/api/v1/command", "/pms/x?token=secret"})
    void unsafeDeveloperRegistrationAlsoFailsAtStartup(String path) {
        assertThrows(IllegalArgumentException.class, () -> new TemplatePresentationRoutes(List.of(provider(path))));
    }

    @Test
    void parametersStayStringsAndNoObjectMeansListWithoutInventingAnId() {
        var page = TemplateExecutionConfiguration.read(source(false).getTasks().getFirst().getExecution()).presentation();
        assertEquals(Map.of("projectId", "9007199254740993"), TemplatePresentationContract.resolve(page, 9007199254740993L, null));
        assertEquals(Map.of("projectId", "9007199254740993", "objectId", "9007199254740994"),
                TemplatePresentationContract.resolve(page, 9007199254740993L, "9007199254740994"));
    }
}
