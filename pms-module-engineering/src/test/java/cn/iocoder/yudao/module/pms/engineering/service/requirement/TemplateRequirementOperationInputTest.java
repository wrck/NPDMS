package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateRequirementOperationInputTest {
    final RequirementAnalysisEntityCommands commands = mock(RequirementAnalysisEntityCommands.class);
    final RequirementAnalysisAccess access = mock(RequirementAnalysisAccess.class);
    RequirementAnalysisOperationCommandAdapter adapter;
    MockedStatic<SecurityFrameworkUtils> security;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        adapter = new RequirementAnalysisOperationCommandAdapter(of(commands), of(access));
        when(access.isManager(eq(9L), any())).thenReturn(true);
        var row = new RequirementAnalysisRevisionDO(); row.setId(11L); row.setEntityId(12L); row.setTenantId(1L);
        row.setProjectId(9L); row.setVersion(2); row.setRevisionNo(1); row.setRevisionState("DRAFT");
        when(access.read(eq(11L), any())).thenReturn(row);
        var result = row.revisionMetadata();
        when(commands.create(any(), any(), anyString())).thenReturn(result);
        when(commands.save(any(), eq(2), any(), any(), anyString())).thenReturn(result);
        when(commands.complete(any(), eq(2), any(), any(), anyString())).thenReturn(result);
        when(commands.copy(any(), eq(2), any(), any(), anyString())).thenReturn(result);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource({"CREATE,{}", "SAVE,'{\"values\":{\"tenantId\":\"field\"},\"expectedExtensionVersion\":3}'",
            "COMPLETE,'{\"reason\":\"完成原因\"}'", "COPY,'{\"reason\":\"新草稿原因\"}'"})
    void originalCommandsRetainTheirBusinessInputAndRevision(String action, String json) {
        var command = command(action, json); String before = command.input().toString();
        assertEquals("11", adapter.invoke("SOL.REQUIREMENT_ANALYSIS." + action, command).objectId());
        assertEquals(before, command.input().toString());
        switch (action) {
            case "CREATE" -> verify(commands).create(argThat(c -> c.projectId() == 9L), any(), anyString());
            case "SAVE" -> verify(commands).save(argThat(ref -> ref.revisionId() == 11L), eq(2),
                    argThat(c -> c.expectedExtensionVersion() == 3 && c.values().get("tenantId").equals("field")), any(), anyString());
            case "COMPLETE" -> verify(commands).complete(any(), eq(2), argThat(c -> "完成原因".equals(c.reason())), any(), anyString());
            case "COPY" -> verify(commands).copy(any(), eq(2), argThat(c -> "新草稿原因".equals(c.reason())), any(), anyString());
            default -> fail(action);
        }
    }

    @ParameterizedTest
    @CsvSource({"CREATE,'{\"values\":{}}'", "SAVE,'{\"execution\":null}'", "SAVE,'{\"tenantId\":2}'",
            "SAVE,'{\"expectedExtensionVersion\":1.9}'", "SAVE,'{\"expectedExtensionVersion\":null}'",
            "SAVE,'{\"className\":\"java.lang.Runtime\"}'", "SAVE,[]", "COMPLETE,'{\"reason\":1}'",
            "COPY,'{\"reason\":{}}'", "COPY,'{\"reason\":\"yes\",\"userId\":9}'"})
    void ignoresNoUnexpectedOrMalformedBusinessInput(String action, String json) {
        assertEquals(400, assertThrows(ServiceException.class,
                () -> adapter.invoke("SOL.REQUIREMENT_ANALYSIS." + action, command(action, json))).getCode());
        verifyNoInteractions(commands);
    }

    @Test void permissionIsStillCheckedBeforeBinding() {
        when(access.isManager(eq(9L), any())).thenReturn(false);
        assertEquals(403, assertThrows(ServiceException.class,
                () -> adapter.invoke("SOL.REQUIREMENT_ANALYSIS.SAVE", command("SAVE", "{}"))).getCode());
        verifyNoInteractions(commands);
    }
    private static ProjectOperationCommand command(String action, String json) {
        return new ProjectOperationCommand(9L, "TASK", 10L, null, action.equals("CREATE") ? null : "11",
                2, "v2", JsonUtils.parseTree(json), "key");
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
