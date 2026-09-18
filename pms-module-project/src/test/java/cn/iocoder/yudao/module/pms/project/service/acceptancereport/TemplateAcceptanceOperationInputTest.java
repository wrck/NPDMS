package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateAcceptanceOperationInputTest {
    static final String PREFIX = "ACC.ACCEPTANCE_REPORT.";
    static ValidatorFactory validation;
    final AcceptanceReportCommandService commands = mock(AcceptanceReportCommandService.class);
    final AcceptanceReportQueryService queries = mock(AcceptanceReportQueryService.class);
    final AcceptanceReportOperationAccessProvider access = mock(AcceptanceReportOperationAccessProvider.class);
    AcceptanceReportOperationCommandAdapter adapter;
    MockedStatic<SecurityFrameworkUtils> security;

    @BeforeAll static void createValidator() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { validation.close(); }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        adapter = new AcceptanceReportOperationCommandAdapter(of(commands), of(queries), of(access), of(validation.getValidator()));
        when(access.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(
                Set.of(PREFIX + "CREATE_DRAFT", PREFIX + "UPDATE_DRAFT", PREFIX + "PUBLISH", PREFIX + "REVOKE"), "v2"));
        when(queries.get(eq(11L), any())).thenReturn(new AcceptanceReportQueryService.ActivityView(11L, 9L, null, null,
                "FINAL", "ACTIVE", 12L, 2, null));
        var result = new AcceptanceReportCommands.ReportResult(11L, 12L, 1, "DRAFT", null, false);
        when(commands.createDraft(any(), any())).thenReturn(result); when(commands.updateDraft(any(), any())).thenReturn(result);
        when(commands.publish(any(), any())).thenReturn(result); when(commands.revoke(any(), any())).thenReturn(result);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource({"CREATE_DRAFT,{}", "UPDATE_DRAFT,'{\"reportVersionId\":\"12\",\"expectedReportVersionNo\":1}'",
            "PUBLISH,'{\"reportVersionId\":\"12\",\"expectedReportVersionNo\":1,\"expectedCurrentReportVersionId\":12}'",
            "REVOKE,'{\"expectedCurrentReportVersionId\":12,\"expectedCurrentReportVersionNo\":1}'"})
    void eachOriginalCommandReceivesItsUnchangedIdentityAndVersions(String action, String json) {
        var input = command(json); String before = JsonUtils.toJsonString(input.input());
        assertEquals("11", adapter.invoke(PREFIX + action, input).objectId());
        assertEquals(before, JsonUtils.toJsonString(input.input()));
        switch (action) {
            case "CREATE_DRAFT" -> verify(commands).createDraft(argThat(c -> c.acceptanceId() == 11L && c.expectedActivityVersion() == 2), any());
            case "UPDATE_DRAFT" -> verify(commands).updateDraft(argThat(c -> c.acceptanceId() == 11L && c.reportVersionId() == 12L && c.expectedReportVersionNo() == 1), any());
            case "PUBLISH" -> verify(commands).publish(argThat(c -> c.acceptanceId() == 11L && c.expectedCurrentReportVersionId() == 12L && c.expectedActivityVersion() == 2), any());
            case "REVOKE" -> verify(commands).revoke(argThat(c -> c.acceptanceId() == 11L && c.expectedCurrentReportVersionNo() == 1), any());
            default -> fail(action);
        }
    }

    @ParameterizedTest
    @CsvSource({"CREATE_DRAFT,'{\"unknown\":true}'", "CREATE_DRAFT,'{\"tenantId\":null}'",
            "CREATE_DRAFT,'{\"execution\":null}'", "CREATE_DRAFT,[]",
            "UPDATE_DRAFT,'{\"reportVersionId\":12,\"expectedReportVersionNo\":0}'",
            "UPDATE_DRAFT,'{\"reportVersionId\":12,\"expectedReportVersionNo\":1.5}'",
            "PUBLISH,'{\"reportVersionId\":12,\"expectedReportVersionNo\":1,\"conclusionCode\":\"PASS\"}'",
            "PUBLISH,'{\"reportVersionId\":12,\"expectedReportVersionNo\":0}'",
            "REVOKE,'{\"expectedCurrentReportVersionId\":12,\"expectedCurrentReportVersionNo\":1,\"actorId\":8}'",
            "REVOKE,'{\"expectedCurrentReportVersionId\":-1,\"expectedCurrentReportVersionNo\":1}'"})
    void malformedOrUnconsumedInputCannotReachTheOwnerWriter(String action, String json) {
        assertEquals(400, assertThrows(ServiceException.class, () -> adapter.invoke(PREFIX + action, command(json))).getCode());
        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"conclusionCode", "conclusionText", "acceptorName"})
    void retainsOriginalDraftRequestLengthConstraints(String field) {
        var input = JsonUtils.parseTree("{\"" + field + "\":\"" + "x".repeat(2001) + "\"}");
        assertEquals(400, assertThrows(ServiceException.class,
                () -> adapter.invoke(PREFIX + "CREATE_DRAFT", command(input.toString()))).getCode());
        verifyNoInteractions(commands);
    }

    @Test void permissionOrVersionFailureRemainsAWriteBarrier() {
        when(access.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(), "v2"));
        assertEquals(403, assertThrows(ServiceException.class, () -> adapter.invoke(PREFIX + "CREATE_DRAFT", command("{}"))).getCode());
        verifyNoInteractions(commands, queries);
    }

    private static ProjectOperationCommand command(String input) {
        return new ProjectOperationCommand(9L, "TASK", 10L, null, "11", 2, "v2", JsonUtils.parseTree(input), "same-key");
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
