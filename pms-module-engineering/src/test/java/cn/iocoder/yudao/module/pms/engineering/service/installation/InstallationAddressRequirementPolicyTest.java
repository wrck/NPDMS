package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceProductQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InstallationAddressRequirementPolicyTest {
    final ConfigApi configs = mock(ConfigApi.class);
    final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    final InstallationAddressRequirementPolicy policy = new InstallationAddressRequirementPolicy(configs, devices);

    @BeforeEach
    void setup() { TenantContextHolder.setTenantId(1L); }

    @AfterEach
    void clear() { TenantContextHolder.clear(); }

    ProjectDeviceInstallationQuery query() { return new ProjectDeviceInstallationQuery(1L, 9L); }

    @Test
    void missingOrEmptyConfigurationDoesNotQueryAssetAndForeignTenantIsRejectedFirst() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.inspect(new ProjectDeviceInstallationQuery(2L, 9L)));
        verifyNoInteractions(configs, devices);

        when(configs.getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY))
                .thenReturn(null, "{\"productFilters\":[]}");
        assertEquals(Set.of(), policy.inspect(query()).exemptDeviceIds());
        assertEquals(Set.of(), policy.inspect(query()).exemptDeviceIds());
        verify(configs, times(2)).getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY);
        verifyNoInteractions(devices);
    }

    @Test
    void configuredFiltersPassThroughToAssetAndUseReturnedDeviceIds() {
        when(configs.getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY))
                .thenReturn("{\"productFilters\":[{\"productTypeIn\":[\"TYPE-A\"]}]}");
        when(devices.findProjectDeviceIdsByProductFilter(any())).thenReturn(List.of(10L, 11L));

        var decision = policy.inspect(query());

        assertEquals(Set.of(10L, 11L), decision.exemptDeviceIds());
        ArgumentCaptor<ProjectDeviceProductQuery> captor = ArgumentCaptor.forClass(ProjectDeviceProductQuery.class);
        verify(devices).findProjectDeviceIdsByProductFilter(captor.capture());
        assertEquals(1L, captor.getValue().tenantId());
        assertEquals(9L, captor.getValue().projectId());
        assertEquals(cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterMatchMode.NOT_MATCHING, captor.getValue().matchMode());
        assertEquals(1, captor.getValue().productFilters().size());
        assertEquals(List.of("TYPE-A"), captor.getValue().productFilters().getFirst().getProductTypeIn());
        verify(devices, never()).getProjectDeviceSummaries(any());
        verify(devices, never()).lockProjectInstallationDevices(any());
    }

    @Test
    void configurationIsReadAgainAndChangedExemptIdsChangeTheVersion() {
        when(configs.getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY))
                .thenReturn("{\"productFilters\":[{\"productCodeLike\":\"A%\"}]}");
        when(devices.findProjectDeviceIdsByProductFilter(any())).thenReturn(List.of(10L), List.of(11L));

        var first = policy.inspect(query());
        var second = policy.inspect(query());

        assertEquals(Set.of(10L), first.exemptDeviceIds());
        assertEquals(Set.of(11L), second.exemptDeviceIds());
        assertNotEquals(first.version(), second.version());
        verify(configs, times(2)).getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY);
        verify(devices, times(2)).findProjectDeviceIdsByProductFilter(any());
        verify(devices, never()).getProjectDeviceSummaries(any());
        verify(devices, never()).lockProjectInstallationDevices(any());
    }

    @Test
    void invalidConfigurationDoesNotQueryAsset() {
        when(configs.getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY))
                .thenReturn("{\"productFilters\":[{}]}");

        assertThrows(IllegalArgumentException.class, () -> policy.inspect(query()));

        verify(configs).getConfigValueByKey(InstallationAddressRequirementPolicy.CONFIG_KEY);
        verifyNoInteractions(devices);
    }
}
