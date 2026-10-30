package cn.iocoder.yudao.module.pms.project.api.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectFieldRuleApiImplTest {
    static RuleEngineTestFixture engine;
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final ProjectRuleFields fields = new ProjectRuleFields(key -> null);
    final ProjectMasterDO project = new ProjectMasterDO();
    final ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
    final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
    ProjectFieldRuleApiImpl api;
    final ProjectFieldRuleApi.Query query = new ProjectFieldRuleApi.Query(1L, 7L, 9L, List.of("major", "ordinary"));
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        var factory = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        factory.addBean("templates", templates);
        api = new ProjectFieldRuleApiImpl(projects, factory.getBeanProvider(ProjectTemplateService.class), scopes, fields, engine.evaluator());
        project.setId(9L); project.setTenantId(1L); project.setVersion(12L);
        project.setLifecycleTemplateId(90L); project.setLifecycleTemplateRevisionId(91L); project.setLifecycleTemplateRevisionNo(3);
        revision.setId(91L); revision.setTenantId(1L); revision.setTemplateId(90L); revision.setRevisionNo(3); revision.setStatus("PUBLISHED");
        when(projects.selectById(9L)).thenReturn(project); when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        when(templates.getRevisionById(91L)).thenReturn(revision); when(templates.getExecutionSnapshot(90L, 3)).thenReturn(snapshot);
        var scope = new ProjectScopeResult(9L, 2L, Set.of(9L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        programs("project.majorProjectLevel");
    }
    void programs(String field) {
        var compiler = new ProjectRuleCompiler();
        var values = List.of("办事处级重大项目", "市场部级重大项目", "公司级重大项目");
        var major = Map.of("predicate", "FIELD", "parameters", Map.of("fieldCode", field, "valueType", "TEXT", "operator", "in", "value", values));
        var ordinary = Map.of("operator", "ALL", "rules", List.of(
                Map.of("predicate", "FIELD", "parameters", Map.of("fieldCode", field, "valueType", "TEXT", "operator", "notNull")),
                Map.of("predicate", "FIELD", "parameters", Map.of("fieldCode", field, "valueType", "TEXT", "operator", "!=", "value", "")),
                Map.of("operator", "NOT", "rules", List.of(major))));
        snapshot.getRulePrograms().put("major", compiler.compile(JsonUtils.parseTree(JsonUtils.toJsonString(major))));
        snapshot.getRulePrograms().put("ordinary", compiler.compile(JsonUtils.parseTree(JsonUtils.toJsonString(ordinary))));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @ParameterizedTest @ValueSource(strings = {"办事处级重大项目", "市场部级重大项目", "公司级重大项目"})
    void approvedCrmValuesRequireMajorReviewAndRetainSourceVersion(String value) {
        project.setMajorProjectLevel(value);
        var result = api.lockAndEvaluate(query);
        assertEquals(Map.of("major", "MATCHED", "ordinary", "NOT_MATCHED"), result.outcomes());
        var evidence = JsonUtils.parseTree(result.evidenceJson());
        assertEquals(value, evidence.path("facts").path("project.majorProjectLevel").path("value").asText());
        assertEquals(12, evidence.path("projectVersion").asInt()); assertEquals(91L, evidence.path("templateRevisionId").asLong());
        verify(projects).selectByIdForUpdate(9L);
    }
    @ParameterizedTest @NullAndEmptySource
    void missingCrmNeverBecomesOrdinary(String value) {
        project.setMajorProjectLevel(value);
        assertEquals(Map.of("major", "NOT_MATCHED", "ordinary", "NOT_MATCHED"), api.evaluate(query).outcomes());
    }
    @Test void unlistedValueIsOrdinaryAndAnotherConfiguredEntityFieldNeedsNoCodeChange() {
        project.setMajorProjectLevel("普通项目");
        assertEquals("MATCHED", api.evaluate(query).outcomes().get("ordinary"));
        programs("project.projectName"); project.setProjectName("公司级重大项目");
        assertEquals("MATCHED", api.evaluate(query).outcomes().get("major"));
    }
    @Test void absentConfigurationDiffersFromIncompleteConfiguration() {
        snapshot.getRulePrograms().clear(); assertFalse(api.evaluate(query).configured());
        programs("project.majorProjectLevel"); snapshot.getRulePrograms().remove("ordinary");
        assertThrows(IllegalArgumentException.class, () -> api.evaluate(query));
    }
    @Test void unboundLegacyProjectRemainsUnconfiguredButPartialReferencesFail() {
        project.setLifecycleTemplateId(null); project.setLifecycleTemplateRevisionId(null); project.setLifecycleTemplateRevisionNo(null);
        assertFalse(api.evaluate(query).configured()); verifyNoInteractions(templates);
        project.setLifecycleTemplateId(90L);
        assertThrows(IllegalArgumentException.class, () -> api.evaluate(query));
    }
    @Test void tenantScopeAndPublicationIdentityFailClosed() {
        assertThrows(RuntimeException.class, () -> api.evaluate(new ProjectFieldRuleApi.Query(2L,7L,9L,query.ruleKeys())));
        revision.setId(92L); assertThrows(RuntimeException.class, () -> api.evaluate(query));
        revision.setId(91L); when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,2L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> api.evaluate(query));
    }
}
