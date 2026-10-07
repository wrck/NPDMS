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
    final SiteSurveyEntityCommands service = mock(SiteSurveyEntityCommands.class);
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
        var row = new SiteSurveyEntityDO(); row.setId(11L); row.setProjectId(9L); row.setTenantId(1L); row.setVersion(2L); row.setStatus(0);
        when(mapper.selectOperationIdentity(any())).thenReturn(row); when(mapper.selectById(11L)).thenReturn(row);
        when(service.executeReceipt(anyString(),nullable(Long.class),nullable(Long.class),nullable(cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO.class),any(),anyString(),any())).thenAnswer(call -> {
            boolean deleted="delete".equals(call.getArgument(0));long version=deleted ? 3L : 2L;
            var result=new SiteSurveyBusinessApplicationService.Result("11","9",version,deleted ? "DELETED" : "0",deleted);
            return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt(
                    cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome.SAVED,
                    new cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef(1L,"SOL","siteSurvey",11L),version,
                    java.util.List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ResultReference(
                            cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ResultReference.Kind.COMMAND,"SOL",JsonUtils.toJsonString(result))),null,null);
        });
        adapter = new SiteSurveyOperationCommandAdapter(of(service), of(mapper), of(scopes), of(permissions), of(validation.getValidator()));
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "DELETE", "CONFIRM", "REJECT", "ARCHIVE"})
    void preservesEveryOriginalActionAndAuthorization(String action) {
        boolean saving = action.equals("CREATE") || action.equals("UPDATE");
        var command = command(action, saving ? "{\"name\":\"现场工勘\",\"businessValues\":{\"tenantId\":\"业务字段\"}}" : "{}");
        String before = command.input().toString();
        assertEquals("11", adapter.invoke("SOL.SITE_SURVEY." + action, command).objectId());
        assertEquals(before, command.input().toString());
        String publicCode=action.equals("UPDATE") ? "save" : action.toLowerCase(java.util.Locale.ROOT);
        verify(service).executeReceipt(eq(publicCode),action.equals("CREATE") ? isNull() : eq(11L),action.equals("CREATE") ? isNull() : eq(2L),
                saving ? argThat(c -> c.getProjectId().equals(9L) && (action.equals("CREATE") ? c.getId()==null : c.getId().equals(11L) && c.getVersion().equals(2L))
                        && c.getName().equals("现场工勘") && c.getBusinessValues().get("tenantId").equals("业务字段")) : isNull(),
                isNull(),eq("key"),eq(cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.PROJECT_NODE));
        verify(permissions).hasAnyPermissions(7L,"pms:sol-site-survey:"+(action.equals("CREATE") ? "create" : action.equals("DELETE") ? "delete" : "update"));
        if(!action.equals("CREATE"))verify(mapper).selectOperationIdentity(any());

    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"tenantId\":null}", "{\"execution\":null}", "{\"@class\":\"java.lang.Runtime\"}",
            "{\"userId\":7}", "{\"projectId\":99}", "{\"id\":99}", "{\"version\":1}",
            "{\"version\":2.5}", "{\"unexpected\":true}", "{\"code\":\"FORGED\"}", "{\"addressId\":111}", "[]"})
    void invalidInputNeverOverridesIdentityOrReachesOwnerWrite(String json) {
        var input = JsonUtils.parseTree(json);
        if (input.isObject()) { ((tools.jackson.databind.node.ObjectNode) input).put("name", "工勘"); }
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
        assertThrows(ServiceException.class, () -> adapter.invoke("SOL.SITE_SURVEY.CREATE", command("CREATE", "{\"name\":\"\"}")));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of()));
        assertEquals(403, assertThrows(ServiceException.class,
                () -> adapter.invoke("SOL.SITE_SURVEY.UPDATE", command("UPDATE", "{}"))).getCode());
        verifyNoInteractions(service);
    }
    private static ProjectOperationCommand command(String action, String json) {
        return new ProjectOperationCommand(9L, "TASK", 10L, null, action.equals("CREATE") ? null : "11",
                action.equals("CREATE") ? null : 2L, "v2", JsonUtils.parseTree(json), "key");
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
