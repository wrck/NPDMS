package cn.iocoder.yudao.module.pms.customer.service.customer;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerExternalMappingMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerCommandResult;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerLifecycleCommand;
import cn.iocoder.yudao.module.pms.customer.service.guard.CustomerDeletionGuardResult;
import cn.iocoder.yudao.module.pms.customer.service.guard.CustomerDeletionGuardService;
import cn.iocoder.yudao.module.pms.customer.service.history.CustomerHistoryService;
import cn.iocoder.yudao.module.pms.customer.service.outbox.CustomerOutboxPayloadFactory;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationInput;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_DELETE_GUARD_BLOCKED;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_SCOPE_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerLifecycleApplicationServiceTest {

    @Mock PlatformCommandExecutionApi platformCommandExecutionApi;
    @Mock CustomerMasterMapper customerMasterMapper;
    @Mock CustomerExternalMappingMapper customerExternalMappingMapper;
    @Mock CustomerHistoryService customerHistoryService;
    @Mock CustomerDeletionGuardService deletionGuardService;
    @Mock CustomerClassificationAccessService classificationAccessService;
    @Mock CustomerScopeContextService scopeContextService;
    @Spy CustomerOutboxPayloadFactory outboxPayloadFactory = new CustomerOutboxPayloadFactory();
    @InjectMocks CustomerApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setTenantId(1L);
        user.setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        lenient().when(scopeContextService.resolve(1L, 7L))
                .thenReturn(new CustomerVisibleScope(true, List.of()));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void disableUsesCasAndPreservesRow() {
        executeAsNew();
        CustomerMasterDO current = customer(CustomerLifecycleStatus.ENABLED, 2);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerMasterMapper.updateLifecycleByVersion(any())).thenReturn(1);

        CustomerCommandResult result = service.disable(command());

        assertEquals(3L, result.version());
        verify(customerHistoryService).appendLifecycle(current, CustomerLifecycleStatus.DISABLED,
                "业务停用", 7L, "life-key");
    }

    @Test
    void administratorCanDisableMigratedCustomerWithoutClassificationSnapshot() {
        executeAsNew();
        CustomerMasterDO current = customer(CustomerLifecycleStatus.ENABLED, 2);
        current.setDepartmentCode(null);
        current.setMarketCode(null);
        current.setSystemCode(null);
        current.setExpendCode(null);
        current.setIndustryCode(null);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerMasterMapper.updateLifecycleByVersion(any())).thenReturn(1);

        CustomerCommandResult result = service.disable(command());

        assertEquals(3L, result.version());
        verify(classificationAccessService, never()).validate(any(), any(), any());
    }

    @Test
    void lifecycleDenialHasNoWriteSideEffect() {
        executeAsNew();
        when(customerMasterMapper.selectById(100L)).thenReturn(customer(CustomerLifecycleStatus.ENABLED, 2));
        when(scopeContextService.resolve(1L, 7L)).thenReturn(new CustomerVisibleScope(false, List.of()));
        when(classificationAccessService.validate(any(), any(CustomerClassificationInput.class), any()))
                .thenThrow(new IllegalStateException("scope denied"));

        assertThrows(IllegalStateException.class, () -> service.disable(command()));

        verify(customerMasterMapper, never()).updateLifecycleByVersion(any());
        verify(customerHistoryService, never()).appendLifecycle(any(), any(), any(), any(), any());
    }

    @Test
    void nonAdministratorCannotDisableMigratedCustomerWithoutClassificationSnapshot() {
        executeAsNew();
        CustomerMasterDO current = customer(CustomerLifecycleStatus.ENABLED, 2);
        current.setDepartmentCode(null);
        current.setMarketCode(null);
        current.setSystemCode(null);
        current.setExpendCode(null);
        current.setIndustryCode(null);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(scopeContextService.resolve(1L, 7L)).thenReturn(new CustomerVisibleScope(false, List.of()));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.disable(command()));

        assertEquals(CUSTOMER_SCOPE_DENIED.getCode(), exception.getCode());
        verify(customerMasterMapper, never()).updateLifecycleByVersion(any());
    }

    @Test
    void deleteFailsClosedWhenGuardIsNotClear() {
        executeAsNew();
        when(customerMasterMapper.selectById(100L)).thenReturn(customer(CustomerLifecycleStatus.ENABLED, 2));
        when(deletionGuardService.check(1L, 100L)).thenReturn(new CustomerDeletionGuardResult(
                false, CustomerReferenceGuardStatus.UNKNOWN, 0, List.of()));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.delete(command()));

        assertEquals(CUSTOMER_DELETE_GUARD_BLOCKED.getCode(), exception.getCode());
    }

    @Test
    void restoreDeletedCustomerUsesOriginalIdentity() {
        executeAsNew();
        CustomerMasterDO current = customer(CustomerLifecycleStatus.DELETED, 2);
        when(customerMasterMapper.selectIncludingDeleted(1L, 100L)).thenReturn(current);
        when(customerMasterMapper.updateLifecycleByVersion(any())).thenReturn(1);

        CustomerCommandResult result = service.restore(command());

        assertEquals(100L, result.customerId());
        assertEquals(3L, result.version());
    }

    private void executeAsNew() {
        when(platformCommandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<CustomerCommandResult> operation = invocation.getArgument(3);
            CustomerCommandResult result = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }

    private CustomerLifecycleCommand command() {
        return new CustomerLifecycleCommand(1L, 100L, "业务停用", 2L, "life-key");
    }

    private CustomerMasterDO customer(CustomerLifecycleStatus status, int version) {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(100L);
        customer.setTenantId(1L);
        customer.setCode("CUS-001");
        customer.setDepartmentCode("D01");
        customer.setMarketCode("M01");
        customer.setSystemCode("S01");
        customer.setExpendCode("E01");
        customer.setIndustryCode("I01");
        customer.setSourceType("PLATFORM_CREATED");
        customer.setLifecycleStatus(status.name());
        customer.setVersion(version);
        return customer;
    }
}
