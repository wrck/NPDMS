package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryMaterialServiceAuthorizationTest {
    @Test
    void absentAuthenticatedOwnerAuthorityCannotWithdrawActiveMaterial() {
        TenantContextHolder.setTenantId(1L);
        try {
            var mapper=mock(DeliveryMaterialMapper.class);
            var row=new DeliveryMaterialDO();
            row.setId(8L);row.setTenantId(1L);row.setOwnerModule("ACC");
            row.setEntityType("project_deliverable");row.setEntityId(42L);
            row.setProjectId(42L);row.setTypeCode("RECEIPT");row.setStatus("ACTIVE");
            when(mapper.selectById(8L)).thenReturn(row);
            var service=new DeliveryMaterialService(mapper,mock(DeliveryCatalogService.class),
                    mock(FileEvidenceApi.class),mock(DeliveryEventPublisher.class),List.of());
            assertThrows(BusinessContractException.class,()->service.withdraw(8L));
            assertEquals("ACTIVE",row.getStatus());
        } finally { TenantContextHolder.clear(); }
    }
}
