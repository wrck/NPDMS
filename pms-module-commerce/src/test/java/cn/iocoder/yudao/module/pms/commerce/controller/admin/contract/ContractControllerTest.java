package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractDetailRespVO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationCommandService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractControllerTest {

    private static final Long TENANT_ID = 0L;
    private static final Long USER_ID = 992002800002L;

    private ContractAccessService accessService;
    private PermissionApi permissionApi;
    private ContractController controller;

    @BeforeEach
    void setUp() {
        accessService = mock(ContractAccessService.class);
        permissionApi = mock(PermissionApi.class);
        controller = new ContractController(accessService, mock(ContractRelationCommandService.class),
                permissionApi, mock(Environment.class));
        LoginUser loginUser = new LoginUser().setId(USER_ID).setTenantId(TENANT_ID).setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void shouldMaskSensitiveFieldsInPageWithoutIndependentPermission() {
        ContractDO contract = contract();
        when(permissionApi.hasAnyPermissions(USER_ID, "pms:commerce:contract:sensitive-read")).thenReturn(false);
        when(accessService.pageContracts(eq(TENANT_ID), eq(USER_ID), anyString(), any()))
                .thenReturn(new PageResult<>(List.of(contract), 1L));

        ContractRespVO response = controller.page(null, null, null, null, null, null,
                1, 20).getData().getList().getFirst();

        assertEquals("CT-001", response.contractNo());
        assertEquals("合同一", response.contractName());
        assertNull(response.contractType());
        assertNull(response.customerCode());
        assertNull(response.customerName());
        assertNull(response.currencyCode());
        verify(permissionApi).hasAnyPermissions(USER_ID, "pms:commerce:contract:sensitive-read");
    }

    @Test
    void shouldExposeSensitiveFieldsInDetailWithIndependentPermission() {
        ContractDO contract = contract();
        when(permissionApi.hasAnyPermissions(USER_ID, "pms:commerce:contract:sensitive-read")).thenReturn(true);
        when(accessService.getContractDetail(eq(TENANT_ID), eq(USER_ID), anyString(), eq(contract.getId())))
                .thenReturn(new ContractAccessService.ContractDetail(contract, List.of(), List.of()));

        ContractDetailRespVO detail = controller.get(contract.getId()).getData();
        ContractRespVO response = detail.contract();

        assertEquals("SALES", response.contractType());
        assertEquals("CUSTOMER-001", response.customerCode());
        assertEquals("客户一", response.customerName());
        assertEquals("CNY", response.currencyCode());
        verify(permissionApi).hasAnyPermissions(USER_ID, "pms:commerce:contract:sensitive-read");
    }

    private ContractDO contract() {
        return new ContractDO().setId(992002390001L).setCompanyCode("DPTECH-DEMO")
                .setCompanyName("迪普科技").setContractNo("CT-001").setContractType("SALES")
                .setCustomerCode("CUSTOMER-001").setCustomerName("客户一").setContractName("合同一")
                .setCurrencyCode("CNY").setMasterSourceVersion("1").setStatus("ENABLED").setVersion(0);
    }
}
