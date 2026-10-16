package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenDefinition;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeliveryTemplateFrozenServiceTest {
    private final DeliveryRequirementMapper mapper = mock(DeliveryRequirementMapper.class);
    private final DeliveryRequirementService requirements = mock(DeliveryRequirementService.class);
    private final DeliveryTemplateFrozenService service =
            new DeliveryTemplateFrozenService(mapper, null, null, requirements, null);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void stagedCodeIsReplacedWithFinalCodeUsingAdvancedVersion() {
        TenantContextHolder.setTenantId(1L);
        var row = new DeliveryRequirementDO();
        row.setId(10L);
        row.setRequirementKind(DeliveryRequirementDO.KIND_TEMPLATE_FROZEN);
        row.setTypeCode("D10");
        row.setVersion(3L);
        when(mapper.selectIdForUpdate(any())).thenReturn(row);
        when(mapper.updateById(any(DeliveryRequirementDO.class))).thenAnswer(invocation -> {
            DeliveryRequirementDO updated = invocation.getArgument(0);
            updated.setVersion(updated.getVersion() + 1);
            return 1;
        });
        var definition = new TemplateFrozenDefinition("D20", "交付件", "S1", "T1", true, 1, null, null);

        service.stageTemplateFrozenCodes(List.of(10L));
        assertEquals("~plan:10", row.getTypeCode());
        assertEquals(4L, row.getVersion());
        assertThrows(BusinessContractException.class, () -> service.updateTemplateFrozen(10L, 3, definition));
        service.updateTemplateFrozen(10L, 4, definition);

        assertEquals("D20", row.getTypeCode());
        assertEquals(5L, row.getVersion());
        verify(requirements).refreshStatus(row);
    }
}
