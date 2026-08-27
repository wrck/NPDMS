package cn.iocoder.yudao.module.pms.asset.service.equipment;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.pms.asset.service.location.DeviceLocationEffectiveService;
import cn.iocoder.yudao.module.pms.asset.service.location.SiteLocationTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentLocationEffectiveServiceTest {

    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentVersionMapper versionMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private SiteLocationTreeService treeService;
    @Mock private DeviceLocationEffectiveService deviceLocationEffectiveService;
    private EquipmentLocationEffectiveService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentLocationEffectiveService(
                equipmentMapper, versionMapper, siteMapper, treeService, deviceLocationEffectiveService);
    }

    @Test
    void effectsStructuredLocationAndAppendsVersion() {
        EquipmentDO before = equipment(8L, null, null, null);
        EquipmentDO after = equipment(8L, 21L, 31L, 201L);
        when(equipmentMapper.selectById(8L)).thenReturn(before, after);
        when(equipmentMapper.updateLocationIfMatch(any(EquipmentDO.class), eq(2))).thenReturn(1);
        when(versionMapper.selectMaxVersionNo(8L)).thenReturn(4);
        SiteDO site = new SiteDO();
        site.setId(21L);
        site.setStatus(0);
        when(siteMapper.selectById(21L)).thenReturn(site);
        SiteLocationDO location = new SiteLocationDO();
        location.setId(31L);
        location.setSiteId(21L);
        location.setStatus(0);
        when(treeService.get(31L, null)).thenReturn(location);

        service.effect(command(201L, 21L, 31L, "RESOLVED", "机柜A"));

        verify(versionMapper).insert(argThat((EquipmentVersionDO version) -> version.getVersionNo() == 5
                && "LOCATION_EFFECTIVE".equals(version.getChangeType())));
    }

    @Test
    void unresolvedRemovalClearsStructuredLocationAndReplayIsIdempotent() {
        EquipmentDO before = equipment(8L, 21L, 31L, 200L);
        EquipmentDO after = equipment(8L, null, null, 201L);
        when(equipmentMapper.selectById(8L)).thenReturn(before, after);
        when(equipmentMapper.updateLocationIfMatch(any(EquipmentDO.class), eq(2))).thenReturn(1);
        when(versionMapper.selectMaxVersionNo(8L)).thenReturn(1);

        EquipmentLocationEffectiveCommand removal = command(201L, null, null, "UNRESOLVED", "已拆除");
        service.effect(removal);
        verify(equipmentMapper).updateLocationIfMatch(argThat(value -> value.getSiteId() == null
                && value.getSiteLocationId() == null), eq(2));

        EquipmentDO replayed = equipment(8L, null, null, 201L);
        reset(equipmentMapper, versionMapper);
        when(equipmentMapper.selectById(8L)).thenReturn(replayed);
        service.effect(removal);
        verify(equipmentMapper, never()).updateLocationIfMatch(any(), anyInt());
        verifyNoInteractions(versionMapper);
        assertEquals(201L, replayed.getLocationSourceInstallationId());
    }

    private EquipmentLocationEffectiveCommand command(Long installationId, Long siteId, Long siteLocationId,
                                                       String resolution, String text) {
        return new EquipmentLocationEffectiveCommand(8L, installationId, siteId, siteLocationId, text,
                resolution, "snapshot", LocalDateTime.of(2026, 8, 23, 9, 30));
    }

    private EquipmentDO equipment(Long id, Long siteId, Long siteLocationId, Long installationId) {
        EquipmentDO equipment = new EquipmentDO();
        equipment.setId(id);
        equipment.setSiteId(siteId);
        equipment.setSiteLocationId(siteLocationId);
        equipment.setLocationSourceInstallationId(installationId);
        equipment.setVersion(2);
        return equipment;
    }
}
