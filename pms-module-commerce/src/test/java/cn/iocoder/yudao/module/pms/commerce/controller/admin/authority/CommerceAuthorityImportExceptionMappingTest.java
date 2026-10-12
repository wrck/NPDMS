package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.Code;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityImportBatchReqVO;
import cn.iocoder.yudao.module.pms.commerce.service.authority.CommerceAuthorityImportApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.commerce.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CommerceAuthorityImportExceptionMappingTest {
    private static final String PRIVATE_DETAIL = "source-key-or-custom-payload-must-not-appear";
    private static final String ROUTE = "/api/v1/pms/commerce-authority/import-batches/exception-probe";
    private static final Map<Code, ErrorCode> ERRORS = Map.of(
            Code.INVALID_REQUEST, COMMERCE_AUTHORITY_INVALID_REQUEST,
            Code.TENANT_CONTEXT_MISMATCH, COMMERCE_AUTHORITY_TENANT_CONTEXT_MISMATCH,
            Code.EVENT_PAYLOAD_CONFLICT, COMMERCE_AUTHORITY_EVENT_PAYLOAD_CONFLICT,
            Code.SOURCE_VERSION_CONFLICT, COMMERCE_AUTHORITY_SOURCE_VERSION_CONFLICT,
            Code.SOURCE_VERSION_PAYLOAD_CONFLICT, COMMERCE_AUTHORITY_SOURCE_VERSION_PAYLOAD_CONFLICT,
            Code.OWNER_DATA_CORRUPTED, COMMERCE_AUTHORITY_OWNER_DATA_CORRUPTED,
            Code.PROVIDER_UNAVAILABLE, COMMERCE_AUTHORITY_PROVIDER_UNAVAILABLE);

    @ParameterizedTest
    @EnumSource(Code.class)
    void declaredOwnerFailureIsAMachineReadableBusinessRejectionWithoutExceptionDetails(Code code) throws Exception {
        var rejection = new CommerceAuthorityIngestException(code, PRIVATE_DETAIL,
                new IllegalStateException("nested-private-cause"));
        mvc(rejection).perform(post(ROUTE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ERRORS.get(code).getCode()))
                .andExpect(jsonPath("$.msg").value(code.name()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PRIVATE_DETAIL))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nested-private-cause"))));
    }

    @Test void actualRowVersionGuardCategoryIsPreservedWithoutItsSourceIdentity() throws Exception {
        var rejection = new CommerceAuthorityIngestException(Code.SOURCE_VERSION_CONFLICT,
                "ROW_VERSION_CONFLICT: salesOrder expectedRowVersion mismatch: " + PRIVATE_DETAIL);
        mvc(rejection).perform(post(ROUTE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(COMMERCE_AUTHORITY_SOURCE_VERSION_CONFLICT.getCode()))
                .andExpect(jsonPath("$.msg").value("SOURCE_VERSION_CONFLICT:ROW_VERSION_CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PRIVATE_DETAIL))));
    }

    @Test void arbitraryConflictMessageCannotBeMistakenForTheControlledRowGuard() {
        var controller = controller();
        for (String message : List.of("prefix ROW_VERSION_CONFLICT: " + PRIVATE_DETAIL,
                "ROW_VERSION_CONFLICTING: " + PRIVATE_DETAIL, PRIVATE_DETAIL)) {
            var result = controller.authorityIngestExceptionHandler(new CommerceAuthorityIngestException(Code.SOURCE_VERSION_CONFLICT,message));
            assertEquals(COMMERCE_AUTHORITY_SOURCE_VERSION_CONFLICT.getCode(),result.getCode());
            assertEquals("SOURCE_VERSION_CONFLICT",result.getMsg());
        }
        var wrongCode = controller.authorityIngestExceptionHandler(new CommerceAuthorityIngestException(Code.INVALID_REQUEST,
                "ROW_VERSION_CONFLICT: " + PRIVATE_DETAIL));
        assertEquals(COMMERCE_AUTHORITY_INVALID_REQUEST.getCode(),wrongCode.getCode());
        assertEquals("INVALID_REQUEST",wrongCode.getMsg());
        var noMessage = controller.authorityIngestExceptionHandler(new CommerceAuthorityIngestException(Code.SOURCE_VERSION_CONFLICT,null));
        assertEquals("SOURCE_VERSION_CONFLICT",noMessage.getMsg());
    }

    @Test void invalidOptionalBlankStillFailsTheOriginalDtoContractAndMapsToInvalidRequest() {
        var controller = controller();
        var time = LocalDateTime.of(2026,8,29,12,0);
        var invalidOrder = new CommerceAuthorityImportBatchReqVO.SalesOrderRecord(
                "O-1", "V1", "V2", "001", "SO-1", "0", null, null, null, null,
                CommerceSourceLifecycleStatus.ACTIVE, time, null, null, "", time, time, null, null, null, 0L);
        var request = new CommerceAuthorityImportBatchReqVO("batch", "wm", time, List.of(), List.of(invalidOrder),List.of(),List.of());
        var rejection = assertThrows(CommerceAuthorityIngestException.class,
                () -> controller.toCommand(1L,"ERP","event","correlation",request));
        assertEquals(Code.INVALID_REQUEST,rejection.getCode());
        var result = controller.authorityIngestExceptionHandler(rejection);
        assertEquals(COMMERCE_AUTHORITY_INVALID_REQUEST.getCode(),result.getCode());
        assertEquals("INVALID_REQUEST",result.getMsg());
    }

    @Test void authorizationFailureKeepsTheGlobalForbiddenContract() throws Exception {
        mvc(new AccessDeniedException(PRIVATE_DETAIL)).perform(post(ROUTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.msg").value(FORBIDDEN.getMsg()));
        mvc(new ServiceException(FORBIDDEN)).perform(post(ROUTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(FORBIDDEN.getCode()));
    }

    @Test void unexpectedRuntimeFailureRemainsGlobalInternalError() throws Exception {
        mvc(new IllegalStateException(PRIVATE_DETAIL)).perform(post(ROUTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(INTERNAL_SERVER_ERROR.getCode()))
                .andExpect(jsonPath("$.msg").value(INTERNAL_SERVER_ERROR.getMsg()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PRIVATE_DETAIL))));
    }

    @Test void anotherControllerDoesNotAcquireTheCommerceImportLocalMapping() throws Exception {
        var mvc = standaloneSetup(new UnrelatedController()).setControllerAdvice(globalHandler()).build();
        mvc.perform(post("/unrelated-authority-error"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test void importPayloadLoggingIsDisabledAndLocalHandlerMatchesOnlyTheDeclaredOwnerException() throws Exception {
        var create = CommerceAuthorityImportController.class.getMethod("create",String.class,String.class,String.class,
                CommerceAuthorityImportBatchReqVO.class);
        var logging = create.getAnnotation(ApiAccessLog.class);
        assertFalse(logging.requestEnable()); assertFalse(logging.responseEnable());
        var handler = CommerceAuthorityImportController.class.getMethod("authorityIngestExceptionHandler",CommerceAuthorityIngestException.class);
        assertArrayEquals(new Class<?>[]{CommerceAuthorityIngestException.class},handler.getAnnotation(ExceptionHandler.class).value());
    }

    private static CommerceAuthorityImportController controller() {
        return new CommerceAuthorityImportController(mock(CommerceAuthorityImportApplicationService.class),mock(Environment.class));
    }
    private static GlobalExceptionHandler globalHandler() {
        return new GlobalExceptionHandler("authority-import-error-test",mock(ApiErrorLogCommonApi.class));
    }
    private static MockMvc mvc(RuntimeException failure) {
        return standaloneSetup(new ErrorProbeController(failure)).setControllerAdvice(globalHandler()).build();
    }

    @RestController
    @RequestMapping("/api/v1/pms/commerce-authority/import-batches")
    static class ErrorProbeController extends CommerceAuthorityImportController {
        private final RuntimeException failure;
        ErrorProbeController(RuntimeException failure) {
            super(mock(CommerceAuthorityImportApplicationService.class),mock(Environment.class));this.failure=failure;
        }
        @PostMapping("/exception-probe") public CommonResult<?> probe() { throw failure; }
    }

    @RestController
    static class UnrelatedController {
        @PostMapping("/unrelated-authority-error") public CommonResult<?> probe() {
            throw new CommerceAuthorityIngestException(Code.SOURCE_VERSION_CONFLICT,PRIVATE_DETAIL);
        }
    }
}
