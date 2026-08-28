package cn.iocoder.yudao.module.pms.customer.service.customer;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerSourceType;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerExternalMappingDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerExternalMappingMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CreateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerCommandResult;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.UpdateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.history.CustomerHistoryService;
import cn.iocoder.yudao.module.pms.customer.service.outbox.CustomerOutboxPayloadFactory;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationInput;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationSnapshot;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_EXTERNAL_MAPPING_DUPLICATE;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerApplicationServiceTest {

    @Mock
    private PlatformCommandExecutionApi platformCommandExecutionApi;
    @Mock
    private CustomerMasterMapper customerMasterMapper;
    @Mock
    private CustomerExternalMappingMapper customerExternalMappingMapper;
    @Mock
    private CustomerHistoryService customerHistoryService;
    @Mock
    private CustomerClassificationAccessService classificationAccessService;
    @Mock
    private CustomerScopeContextService scopeContextService;

    @org.mockito.Spy
    private CustomerOutboxPayloadFactory outboxPayloadFactory = new CustomerOutboxPayloadFactory();

    @InjectMocks
    private CustomerApplicationServiceImpl service;

    @BeforeEach
    void setUpContext() {
        TenantContextHolder.setTenantId(1L);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(7L);
        loginUser.setTenantId(1L);
        loginUser.setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
        CustomerVisibleScope scope = new CustomerVisibleScope(true, List.of());
        lenient().when(scopeContextService.resolve(1L, 7L)).thenReturn(scope);
        lenient().when(classificationAccessService.validate(any(), any(CustomerClassificationInput.class), any()))
                .thenReturn(classification());
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsPlatformCustomerInsidePlatformCommand() {
        executeAsNew();
        doAnswer(invocation -> {
            CustomerMasterDO customer = invocation.getArgument(0);
            customer.setId(100L);
            return 1;
        }).when(customerMasterMapper).insert(any(CustomerMasterDO.class));

        CustomerCommandResult result = service.create(platformCreate());

        assertEquals(100L, result.customerId());
        assertEquals(0L, result.version());
        assertFalse(result.replayed());
        ArgumentCaptor<CustomerMasterDO> customerCaptor = ArgumentCaptor.forClass(CustomerMasterDO.class);
        verify(customerMasterMapper).insert(customerCaptor.capture());
        assertEquals(1L, customerCaptor.getValue().getTenantId());
        assertEquals(CustomerLifecycleStatus.ENABLED.name(), customerCaptor.getValue().getLifecycleStatus());
        assertEquals(CustomerSourceType.PLATFORM_CREATED.name(), customerCaptor.getValue().getSourceType());
        assertEquals("D01", customerCaptor.getValue().getDepartmentCode());
        assertEquals("华东办事处", customerCaptor.getValue().getDepartmentName());
        assertEquals("M01", customerCaptor.getValue().getMarketCode());
        assertEquals("市场一部", customerCaptor.getValue().getMarketName());
        assertEquals("S01", customerCaptor.getValue().getSystemCode());
        assertEquals("系统一部", customerCaptor.getValue().getSystemName());
        assertEquals("E01", customerCaptor.getValue().getExpendCode());
        assertEquals("拓展一部", customerCaptor.getValue().getExpendName());
        assertEquals("I01", customerCaptor.getValue().getIndustryCode());
        assertEquals("子行业一", customerCaptor.getValue().getIndustryName());
        verify(customerHistoryService).appendCreate(customerCaptor.getValue(), 7L, "key-create");
        ArgumentCaptor<Function<CustomerCommandResult, PlatformCommandExecutionApi.SuccessFacts>> factsCaptor =
                ArgumentCaptor.forClass(Function.class);
        verify(platformCommandExecutionApi).execute(any(), any(), any(), any(), factsCaptor.capture());
        PlatformCommandExecutionApi.SuccessFacts facts = factsCaptor.getValue().apply(result);
        assertEquals("CUSTOMER_CREATE", facts.operationCode());
        assertEquals("CustomerUpdated", facts.eventType());
        assertFalse(facts.eventPayload().contains("客户甲"));
        assertFalse(facts.eventPayload().contains("交付备注"));
    }

    @Test
    void createsTemporaryCustomerWithReconciliationFacts() {
        executeAsNew();
        doAnswer(invocation -> {
            CustomerMasterDO customer = invocation.getArgument(0);
            customer.setId(101L);
            return 1;
        }).when(customerMasterMapper).insert(any(CustomerMasterDO.class));
        CreateCustomerCommand command = new CreateCustomerCommand(
                1L, "CUS-TEMP", "临时客户", null, null,
                CustomerSourceType.PLATFORM_TEMPORARY, null, null,
                "CRM 暂不可用", true, "D01", "M01", "S01", "E01", "I01", "key-temp");

        CustomerCommandResult result = service.create(command);

        assertEquals(101L, result.customerId());
        ArgumentCaptor<CustomerMasterDO> captor = ArgumentCaptor.forClass(CustomerMasterDO.class);
        verify(customerMasterMapper).insert(captor.capture());
        assertTrue(captor.getValue().getReconciliationPending());
        assertEquals("CRM 暂不可用", captor.getValue().getTemporaryReason());
    }

    @Test
    void sameRequestReplayReturnsReplayedResultWithoutWrites() {
        when(platformCommandExecutionApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                        new CustomerCommandResult(100L, 0L, false)));

        CustomerCommandResult result = service.create(platformCreate());

        assertTrue(result.replayed());
        verifyNoInteractions(customerMasterMapper, customerExternalMappingMapper, customerHistoryService);
    }

    @Test
    void sameKeyDifferentPayloadMapsToStableConflict() {
        when(platformCommandExecutionApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.create(platformCreate()));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), exception.getCode());
        verifyNoInteractions(customerMasterMapper, customerExternalMappingMapper, customerHistoryService);
    }

    @Test
    void classificationDenialHasNoWriteSideEffect() {
        executeAsNew();
        when(classificationAccessService.validate(any(), any(CustomerClassificationInput.class), any()))
                .thenThrow(new IllegalStateException("scope denied"));

        assertThrows(IllegalStateException.class, () -> service.create(platformCreate()));

        verify(customerMasterMapper, never()).insert(any(CustomerMasterDO.class));
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void duplicateCodeStopsBeforeInsert() {
        executeAsNew();
        when(customerMasterMapper.selectByTenantIdAndCode(1L, "CUS-001")).thenReturn(customer(200L, 0));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.create(platformCreate()));

        assertEquals(CUSTOMER_CODE_DUPLICATE.getCode(), exception.getCode());
        verify(customerMasterMapper, never()).insert(any(CustomerMasterDO.class));
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void duplicateCrmMappingStopsBeforeInsert() {
        executeAsNew();
        CustomerExternalMappingDO mapping = new CustomerExternalMappingDO();
        mapping.setCustomerId(200L);
        when(customerExternalMappingMapper.selectCurrent(any())).thenReturn(mapping);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.create(crmCreate()));

        assertEquals(CUSTOMER_EXTERNAL_MAPPING_DUPLICATE.getCode(), exception.getCode());
        verify(customerMasterMapper, never()).insert(any(CustomerMasterDO.class));
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void businessUpdateRejectsCrmOwnedFieldAfterMapping() {
        executeAsNew();
        CustomerMasterDO current = customer(100L, 2);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerExternalMappingMapper.selectCurrentByCustomerId(1L, 100L))
                .thenReturn(new CustomerExternalMappingDO());
        UpdateCustomerCommand command = new UpdateCustomerCommand(
                1L, 100L, "新名称", null, null, Set.of("name"), 2L, "key-update");

        assertThrows(IllegalArgumentException.class, () -> service.update(command));

        verify(customerMasterMapper, never()).updatePlatformFieldsByVersion(any());
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void businessUpdateRejectsCrmOwnedClassificationAfterMapping() {
        executeAsNew();
        CustomerMasterDO current = customer(100L, 2);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerExternalMappingMapper.selectCurrentByCustomerId(1L, 100L))
                .thenReturn(new CustomerExternalMappingDO());
        UpdateCustomerCommand command = classificationUpdate(2L);

        assertThrows(IllegalArgumentException.class, () -> service.update(command));

        verify(customerMasterMapper, never()).updatePlatformFieldsByVersion(any());
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void successfulClassificationUpdateValidatesScopeAndPersistsSnapshot() {
        executeAsNew();
        CustomerMasterDO current = customer(100L, 2);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerMasterMapper.updatePlatformFieldsByVersion(any())).thenReturn(1);
        UpdateCustomerCommand command = classificationUpdate(2L);

        CustomerCommandResult result = service.update(command);

        assertEquals(3L, result.version());
        ArgumentCaptor<CustomerPlatformUpdate> updateCaptor = ArgumentCaptor.forClass(CustomerPlatformUpdate.class);
        verify(customerMasterMapper).updatePlatformFieldsByVersion(updateCaptor.capture());
        assertTrue(updateCaptor.getValue().updateClassification());
        assertEquals("D01", updateCaptor.getValue().departmentCode());
        assertEquals("华东办事处", updateCaptor.getValue().departmentName());
        assertEquals("I01", updateCaptor.getValue().industryCode());
        assertEquals("子行业一", updateCaptor.getValue().industryName());
        verify(classificationAccessService).validate(any(), any(CustomerClassificationInput.class), any());
        verify(customerHistoryService).appendUpdate(current, command, 7L, "key-update-classification");
    }

    @Test
    void classificationUpdateDenialHasNoWriteSideEffect() {
        executeAsNew();
        CustomerMasterDO current = customer(100L, 2);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(classificationAccessService.validate(any(), any(CustomerClassificationInput.class), any()))
                .thenThrow(new IllegalStateException("scope denied"));

        assertThrows(IllegalStateException.class, () -> service.update(classificationUpdate(2L)));

        verify(customerMasterMapper, never()).updatePlatformFieldsByVersion(any());
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void staleVersionHasNoHistorySideEffect() {
        executeAsNew();
        CustomerMasterDO current = customer(100L, 3);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        UpdateCustomerCommand command = new UpdateCustomerCommand(
                1L, 100L, null, null, "更新备注", Set.of("remark"), 2L, "key-update");

        ServiceException exception = assertThrows(ServiceException.class, () -> service.update(command));

        assertEquals(CUSTOMER_VERSION_CONFLICT.getCode(), exception.getCode());
        verify(customerMasterMapper, never()).updatePlatformFieldsByVersion(any());
        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void successfulUpdateUsesCasAndAppendsDigestHistory() {
        executeAsNew();
        CustomerMasterDO current = customer(100L, 2);
        current.setRemark("旧备注");
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerMasterMapper.updatePlatformFieldsByVersion(any())).thenReturn(1);
        UpdateCustomerCommand command = new UpdateCustomerCommand(
                1L, 100L, null, null, "新备注", Set.of("remark"), 2L, "key-update");

        CustomerCommandResult result = service.update(command);

        assertEquals(3L, result.version());
        verify(customerHistoryService).appendUpdate(current, command, 7L, "key-update");
        ArgumentCaptor<CustomerPlatformUpdate> updateCaptor = ArgumentCaptor.forClass(CustomerPlatformUpdate.class);
        verify(customerMasterMapper).updatePlatformFieldsByVersion(updateCaptor.capture());
        assertEquals(1L, updateCaptor.getValue().tenantId());
        assertEquals(2L, updateCaptor.getValue().expectedVersion());
        assertEquals("新备注", updateCaptor.getValue().remark());
    }

    @Test
    void updateUsesLoginUserTenantWhenTenantContextIsDisabled() {
        TenantContextHolder.clear();
        executeAsNew();
        CustomerMasterDO current = customer(100L, 2);
        when(customerMasterMapper.selectById(100L)).thenReturn(current);
        when(customerMasterMapper.updatePlatformFieldsByVersion(any())).thenReturn(1);
        UpdateCustomerCommand command = new UpdateCustomerCommand(
                1L, 100L, null, null, "新备注", Set.of("remark"), 2L, "key-update-no-tenant-context");

        CustomerCommandResult result = service.update(command);

        assertEquals(3L, result.version());
        ArgumentCaptor<CustomerPlatformUpdate> updateCaptor = ArgumentCaptor.forClass(CustomerPlatformUpdate.class);
        verify(customerMasterMapper).updatePlatformFieldsByVersion(updateCaptor.capture());
        assertEquals(1L, updateCaptor.getValue().tenantId());
    }

    @Test
    void outboxPayloadContainsOnlyNonSensitiveEventFacts() {
        CustomerMasterDO customer = customer(100L, 4);
        customer.setName("客户敏感名称");
        customer.setRemark("敏感交付备注");

        String payload = outboxPayloadFactory.customerUpdated(customer, "UPDATED");

        assertNotNull(payload);
        assertTrue(payload.contains("100"));
        assertTrue(payload.contains("UPDATED"));
        assertFalse(payload.contains("客户敏感名称"));
        assertFalse(payload.contains("敏感交付备注"));
    }

    private void executeAsNew() {
        when(platformCommandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<CustomerCommandResult> operation = invocation.getArgument(3);
            CustomerCommandResult result = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }

    private CreateCustomerCommand platformCreate() {
        return new CreateCustomerCommand(
                1L, "CUS-001", "客户甲", "甲", "交付备注",
                CustomerSourceType.PLATFORM_CREATED, null, null,
                null, false, "D01", "M01", "S01", "E01", "I01", "key-create");
    }

    private CreateCustomerCommand crmCreate() {
        return new CreateCustomerCommand(
                1L, "CUS-CRM", "CRM 客户", null, null,
                CustomerSourceType.CRM_SYNC, "CRM-100", "v1",
                null, false, "D01", "M01", "S01", "E01", "I01", "key-crm");
    }

    private CustomerClassificationSnapshot classification() {
        return new CustomerClassificationSnapshot(
                "D01", "华东办事处", "M01", "市场一部", "S01", "系统一部",
                "E01", "拓展一部", "I01", "子行业一");
    }

    private UpdateCustomerCommand classificationUpdate(Long expectedVersion) {
        return new UpdateCustomerCommand(
                1L, 100L, null, null, null,
                "D01", "M01", "S01", "E01", "I01",
                Set.of("classification"), expectedVersion, "key-update-classification");
    }

    private CustomerMasterDO customer(Long id, int version) {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(id);
        customer.setTenantId(1L);
        customer.setCode("CUS-001");
        customer.setName("客户甲");
        customer.setLifecycleStatus(CustomerLifecycleStatus.ENABLED.name());
        customer.setSourceType(CustomerSourceType.PLATFORM_CREATED.name());
        customer.setVersion(version);
        return customer;
    }
}
