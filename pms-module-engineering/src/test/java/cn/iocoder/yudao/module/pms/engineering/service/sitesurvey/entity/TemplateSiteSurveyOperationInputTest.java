package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationCommand;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateSiteSurveyOperationInputTest {
    static ValidatorFactory validation;
    final SiteSurveyEntityService service = mock(SiteSurveyEntityService.class);
    final SiteSurveyEntityMapper mapper = mock(SiteSurveyEntityMapper.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    MockedStatic<SecurityFrameworkUtils> security;
    SiteSurveyOperationCommandAdapter adapter;
    @BeforeAll static void validator() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { validation.close(); }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        when(permissions.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        var row = new SiteSurveyEntityDO(); row.setId(11L); row.setProjectId(9L); row.setTenantId(1L); row.setVersion(2); row.setStatus(0);
        when(mapper.selectTaskObjectForUpdate(any())).thenReturn(row); when(mapper.selectById(11L)).thenReturn(row);
        when(service.createSiteSurveyEntity(any())).thenReturn(11L);
        adapter = new SiteSurveyOperationCommandAdapter(of(service), of(mapper), of(scopes), of(permissions), of(validation.getValidator()));
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "DELETE", "CONFIRM", "REJECT", "ARCHIVE"})
    void preservesEveryOriginalActionAndAuthorization(String action) {
        boolean saving = action.equals("CREATE") || action.equals("UPDATE");
        var command = command(action, saving ? "{\"code\":\"SURVEY\",\"name\":\"现场工勘\",\"businessValues\":{\"tenantId\":\"业务字段\"}}" : "{}");
        String before = command.input().toString();
        assertEquals("11", adapter.invoke("SOL.SITE_SURVEY." + action, command).objectId());
        assertEquals(before, command.input().toString());
        if (action.equals("CREATE")) verify(service).createSiteSurveyEntity(argThat(c -> c.getProjectId() == 9L && c.getId() == null && c.getBusinessValues().get("tenantId").equals("业务字段")));
        if (action.equals("UPDATE")) verify(service).updateSiteSurveyEntity(argThat(c -> c.getId() == 11L && c.getVersion() == 2 && c.getName().equals("现场工勘")));
        if (action.equals("DELETE")) verify(service).deleteSiteSurveyEntity(11L, null);
        if (action.equals("CONFIRM")) verify(service).confirmSiteSurveyEntity(11L, null);
        if (action.equals("REJECT")) verify(service).rejectSiteSurveyEntity(11L, null);
        if (action.equals("ARCHIVE")) verify(service).archiveSiteSurveyEntity(11L, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"tenantId\":null}", "{\"execution\":null}", "{\"@class\":\"java.lang.Runtime\"}",
            "{\"userId\":7}", "{\"projectId\":99}", "{\"id\":99}", "{\"version\":1}",
            "{\"version\":2.5}", "{\"unexpected\":true}", "{\"addressId\":111}", "[]"})
    void invalidInputNeverOverridesIdentityOrReachesOwnerWrite(String json) {
        var input = JsonUtils.parseTree(json);
        if (input.isObject()) { ((tools.jackson.databind.node.ObjectNode) input).put("name", "工勘").put("code", "SURVEY"); }
        assertEquals(400, assertThrows(ServiceException.class,
                () -> adapter.invoke("SOL.SITE_SURVEY.UPDATE", command("UPDATE", input.toString()))).getCode());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE", "CONFIRM", "REJECT", "ARCHIVE"})
    void objectOnlyCommandsCannotSilentlyDiscardInput(String action) {
        assertEquals(400, assertThrows(ServiceException.class,
                () -> adapter.invoke("SOL.SITE_SURVEY." + action, command(action, "{\"status\":3}"))).getCode());
        verifyNoInteractions(service);
    }

    @Test void retainsOriginalBeanValidationAndProjectScope() {
        assertThrows(ServiceException.class, () -> adapter.invoke("SOL.SITE_SURVEY.CREATE", command("CREATE", "{\"name\":\"\",\"code\":\"SURVEY\"}")));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of()));
        assertEquals(403, assertThrows(ServiceException.class,
                () -> adapter.invoke("SOL.SITE_SURVEY.UPDATE", command("UPDATE", "{}"))).getCode());
        verifyNoInteractions(service);
    }
    private static ProjectOperationCommand command(String action, String json) {
        return new ProjectOperationCommand(9L, "TASK", 10L, null, action.equals("CREATE") ? null : "11",
                action.equals("CREATE") ? null : 2, "v2", JsonUtils.parseTree(json), "key");
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
