package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerRelationshipPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectHistoryPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRelationshipQueryServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceAssignmentMapper assignmentMapper;
    private DeviceRelationshipQueryService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceRelationshipQueryService(deviceMapper, assignmentMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadProjectHistoryWithAuthenticatedTenantWhenTenantContextIsDisabled() {
        TenantContextHolder.clear();
        authenticateTenant(1L);
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        DeviceProjectHistoryPageQuery query = new DeviceProjectHistoryPageQuery(1L, "SN-8", 0L, 20L);
        when(assignmentMapper.selectProjectHistoryPage(query))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getProjectHistory(1L, 8L, 1L, 20L);

        verify(assignmentMapper).selectProjectHistoryPage(query);
    }

    @Test
    void shouldReadCustomerRelationshipsWithAuthenticatedTenantWhenTenantContextIsDisabled() {
        TenantContextHolder.clear();
        authenticateTenant(1L);
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        DeviceCustomerRelationshipPageQuery query = new DeviceCustomerRelationshipPageQuery(1L, "SN-8", 0L, 20L);
        when(assignmentMapper.selectCustomerRelationshipPage(query))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getCustomerRelationships(1L, 8L, 1L, 20L);

        verify(assignmentMapper).selectCustomerRelationshipPage(query);
    }

    @Test
    void shouldReadPagedProjectAssignmentHistory() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        DeviceProjectRelationshipDO relationship = new DeviceProjectRelationshipDO();
        relationship.setProjectId(21L);
        DeviceProjectHistoryPageQuery query = new DeviceProjectHistoryPageQuery(1L, "SN-8", 0L, 20L);
        when(assignmentMapper.selectProjectHistoryPage(query))
                .thenReturn(new PageResult<>(List.of(relationship), 1L));

        PageResult<DeviceProjectRelationshipDO> result = service.getProjectHistory(1L, 8L, 1L, 20L);

        assertEquals(21L, result.getList().getFirst().getProjectId());
        verify(assignmentMapper).selectProjectHistoryPage(query);
    }

    @Test
    void shouldReadPagedCustomerRelationships() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        DeviceCustomerRelationshipDO relationship = new DeviceCustomerRelationshipDO();
        relationship.setCustomerId(31L);
        DeviceCustomerRelationshipPageQuery query = new DeviceCustomerRelationshipPageQuery(1L, "SN-8", 0L, 20L);
        when(assignmentMapper.selectCustomerRelationshipPage(query))
                .thenReturn(new PageResult<>(List.of(relationship), 1L));

        PageResult<DeviceCustomerRelationshipDO> result = service.getCustomerRelationships(1L, 8L, 1L, 20L);

        assertEquals(31L, result.getList().getFirst().getCustomerId());
        verify(assignmentMapper).selectCustomerRelationshipPage(query);
    }

    private void authenticateTenant(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        return device;
    }
}
