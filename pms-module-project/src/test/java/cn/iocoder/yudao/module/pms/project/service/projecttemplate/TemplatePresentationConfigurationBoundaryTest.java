package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplatePresentationConfigurationBoundaryTest {
    private final TemplateExecutionConfigurationPersistenceTest fixture = new TemplateExecutionConfigurationPersistenceTest();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void clear() { fixture.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"https://outside.invalid/page", "//outside.invalid/page", "javascript:alert(1)",
            "/pms/../secret", "/pms/%2e%2e/secret", "/pms/%252e%252e/secret", "/pms\\secret",
            "/pms/page?tenantId=1", "/pms/page#fragment", "/admin-api/pms/update", "/api/v1/pms/write"})
    void unsafePageNeverReachesDraftWriterOrCompiledSnapshot(String page) {
        var source = TemplateExecutionConfigurationCompilationTest.source();
        var config = (ObjectNode) source.getTasks().getFirst().getExecution();
        config.set("presentation", JsonUtils.parseTree("{}"));
        ((ObjectNode) config.get("presentation")).put("pageUrl", page);
        assertThrows(IllegalArgumentException.class, () -> fixture.service.updateProjectTemplateDesigner(10L, source));
        verify(fixture.revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
        var compiled = TemplateExecutionConfigurationCompilationTest.compiler("OK").compileVersioned(source);
        assertFalse(compiled.valid()); assertNull(compiled.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenantId", "TENANT_ID", "actorId", "access_token", "redirect", "callbackUrl", "__proto__"})
    void presentationCannotInjectIdentityCredentialOrRedirectParameters(String key) {
        var value = (ObjectNode) JsonUtils.parseTree("{\"presentation\":{\"pageUrl\":\"/pms/page\",\"query\":{}}}");
        ((ObjectNode) value.path("presentation").path("query")).put(key, "value");
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionConfiguration.read(value));
    }

    @Test
    void validPresentationSurvivesSaveReopenButDoesNotPretendAnUninstalledRouteIsRunnable() {
        var source = TemplateExecutionConfigurationCompilationTest.source();
        var config = (ObjectNode) source.getTasks().getFirst().getExecution();
        config.set("presentation", JsonUtils.parseTree("{\"pageUrl\":\"/pms/page\",\"query\":{\"projectId\":\"$project.id\",\"objectId\":\"9007199254740993\"}}"));
        String before = config.toString();
        fixture.service.updateProjectTemplateDesigner(10L, source);
        assertEquals(before, fixture.service.getDraftDesigner(10L).getTasks().getFirst().getExecution().toString());
        var compiled = TemplateExecutionConfigurationCompilationTest.compiler("OK").compileVersioned(source);
        assertFalse(compiled.valid()); assertNull(compiled.snapshot());
        assertTrue(compiled.issues().stream().anyMatch(issue -> issue.code().equals("PRESENTATION_ROUTE_NOT_INSTALLED")));
        verifyNoInteractions(fixture.legacy);
    }
}
