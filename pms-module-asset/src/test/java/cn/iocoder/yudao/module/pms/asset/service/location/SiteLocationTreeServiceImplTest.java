package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationInput;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteLocationTreeServiceImplTest {

    @Mock private SiteLocationMapper mapper;
    @Mock private EquipmentMapper equipmentMapper;

    private final Map<Long, SiteLocationDO> records = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(100L);
    private SiteLocationTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SiteLocationTreeServiceImpl(mapper, equipmentMapper);
        when(mapper.selectById(anyLong())).thenAnswer(invocation -> records.get(invocation.getArgument(0)));
        when(mapper.selectBySiteIdAndCode(anyLong(), anyString())).thenAnswer(invocation -> records.values().stream()
                .filter(item -> item.getSiteId().equals(invocation.getArgument(0))
                        && item.getCode().equals(invocation.getArgument(1))).findFirst().orElse(null));
        lenient().when(mapper.selectListBySiteId(anyLong())).thenAnswer(invocation -> records.values().stream()
                .filter(item -> item.getSiteId().equals(invocation.getArgument(0))).toList());
        doAnswer(invocation -> {
            SiteLocationDO value = invocation.getArgument(0);
            value.setId(ids.incrementAndGet());
            records.put(value.getId(), value);
            return 1;
        }).when(mapper).insert(any(SiteLocationDO.class));
    }

    @Test
    void shouldSupportArbitraryDepthAndRejectDescendantParentCycle() {
        SiteLocationDO parent = null;
        SiteLocationDO root = null;
        for (int depth = 0; depth <= 6; depth++) {
            parent = service.maintain(9L, new SiteLocationInput(null, null,
                    parent == null ? null : parent.getId(), "L-" + depth, "位置" + depth, "SPACE", depth));
            if (depth == 0) {
                root = parent;
            }
        }
        assertEquals(6, parent.getTreeDepth());
        SiteLocationDO finalRoot = root;
        SiteLocationDO finalLeaf = parent;
        assertThrows(ServiceException.class, () -> service.maintain(9L,
                new SiteLocationInput(finalRoot.getId(), 0, finalLeaf.getId(), finalRoot.getCode(),
                        finalRoot.getName(), finalRoot.getLocationType(), finalRoot.getTreeSort())));
    }

    @Test
    void shouldRejectCrossSiteParentAndActiveDescendantDisable() {
        SiteLocationDO siteOneRoot = service.maintain(1L,
                new SiteLocationInput(null, null, null, "ROOT-1", "根1", "SPACE", 0));
        SiteLocationDO child = service.maintain(1L,
                new SiteLocationInput(null, null, siteOneRoot.getId(), "CHILD", "子", "SPACE", 0));
        SiteLocationDO siteTwoRoot = service.maintain(2L,
                new SiteLocationInput(null, null, null, "ROOT-2", "根2", "SPACE", 0));

        assertThrows(ServiceException.class, () -> service.maintain(1L,
                new SiteLocationInput(null, null, siteTwoRoot.getId(), "BAD", "错误", "SPACE", 0)));
        assertThrows(ServiceException.class, () -> service.disable(siteOneRoot.getId(), 0));
        assertEquals(1, child.getTreeDepth());
    }

    @Test
    void shouldResolveIdOnlyReferenceWithoutUpdatingSharedLocation() {
        SiteLocationDO location = service.maintain(1L,
                new SiteLocationInput(null, null, null, "ROOM-1", "机房1", "ROOM", 0));

        SiteLocationDO referenced = service.maintain(1L,
                new SiteLocationInput(location.getId(), 0, null, null, null, null, null));

        assertSame(location, referenced);
        verify(mapper, never()).updateByIdAndVersion(any(), anyInt());
    }

}
