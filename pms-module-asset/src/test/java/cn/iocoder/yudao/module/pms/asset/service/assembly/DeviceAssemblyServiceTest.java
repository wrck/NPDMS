package cn.iocoder.yudao.module.pms.asset.service.assembly;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceAssemblyDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyPathQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblySourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.assembly.command.ApplyDeviceAssemblyCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAssemblyServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceAssemblyMapper assemblyMapper;
    private DeviceAssemblyService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceAssemblyService(deviceMapper, assemblyMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadCurrentTreeWithAuthenticatedTenantWhenTenantContextIsDisabled() {
        TenantContextHolder.clear();
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
        DeviceDO root = device("SN-P");
        root.setId(8L);
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(root);
        when(assemblyMapper.selectCurrentTree(any())).thenReturn(List.of());

        service.getCurrentTree(1L, 8L);

        verify(assemblyMapper).selectCurrentTree(argThat(query ->
                query.tenantId().equals(1L) && query.rootDeviceSn().equals("SN-P")));
    }

    @Test
    void shouldReplaceCurrentChildAndPositionWithHistory() {
        when(deviceMapper.selectByTenantAndSn(1L, "SN-P")).thenReturn(device("SN-P"));
        when(deviceMapper.selectByTenantAndSn(1L, "SN-C")).thenReturn(device("SN-C"));
        when(assemblyMapper.existsBySource(any(DeviceAssemblySourceQuery.class))).thenReturn(false);
        when(assemblyMapper.existsPath(any(DeviceAssemblyPathQuery.class))).thenReturn(false);

        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 27, 15, 0);
        service.apply(new ApplyDeviceAssemblyCommand(
                1L, "SN-P", "SN-C", "SLOT-1", "PHYSICAL", effectiveAt,
                "evidence-1", "AST", "assembly-1", "1"));

        verify(assemblyMapper).closeCurrentByChild(1L, "SN-C", effectiveAt);
        verify(assemblyMapper).closeCurrentByPosition(1L, "SN-P", "SLOT-1", effectiveAt);
        verify(assemblyMapper).insert(argThat((DeviceAssemblyDO value) ->
                "SN-P".equals(value.getParentDeviceSn())
                        && "SN-C".equals(value.getChildDeviceSn())
                        && "SLOT-1".equals(value.getPositionCode())
                        && effectiveAt.equals(value.getEffectiveFrom())));
    }

    @Test
    void shouldIgnoreDuplicateSource() {
        when(assemblyMapper.existsBySource(any(DeviceAssemblySourceQuery.class))).thenReturn(true);

        service.apply(command("SN-P", "SN-C"));

        verify(deviceMapper, never()).selectByTenantAndSn(any(), any());
        verify(assemblyMapper, never()).insert(any(DeviceAssemblyDO.class));
    }

    @Test
    void shouldRejectIndirectCycle() {
        when(deviceMapper.selectByTenantAndSn(1L, "SN-C")).thenReturn(device("SN-C"));
        when(deviceMapper.selectByTenantAndSn(1L, "SN-A")).thenReturn(device("SN-A"));
        when(assemblyMapper.existsPath(new DeviceAssemblyPathQuery(1L, "SN-A", "SN-C")))
                .thenReturn(true);

        assertThrows(ServiceException.class, () -> service.apply(command("SN-C", "SN-A")));

        verify(assemblyMapper, never()).insert(any(DeviceAssemblyDO.class));
    }

    @Test
    void shouldReadCurrentAssemblyTreeFromDeviceSn() {
        DeviceDO root = device("SN-P");
        root.setId(8L);
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(root);
        DeviceAssemblyDO edge = new DeviceAssemblyDO();
        edge.setParentDeviceSn("SN-P");
        edge.setChildDeviceSn("SN-C");
        when(assemblyMapper.selectCurrentTree(any())).thenReturn(List.of(edge));

        List<DeviceAssemblyDO> result = service.getCurrentTree(1L, 8L);

        assertEquals("SN-C", result.getFirst().getChildDeviceSn());
        verify(assemblyMapper).selectCurrentTree(argThat(query ->
                query.tenantId().equals(1L) && query.rootDeviceSn().equals("SN-P")));
    }

    private ApplyDeviceAssemblyCommand command(String parentSn, String childSn) {
        return new ApplyDeviceAssemblyCommand(
                1L, parentSn, childSn, "SLOT-1", "PHYSICAL",
                LocalDateTime.of(2026, 8, 27, 15, 0), null,
                "AST", "assembly-1", "1");
    }

    private DeviceDO device(String sn) {
        DeviceDO device = new DeviceDO();
        device.setTenantId(1L);
        device.setSn(sn);
        return device;
    }
}
