package cn.iocoder.yudao.module.pms.engineering.service.delivery;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.enums.TrainingStatusEnum;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingDeliveryEvidenceProviderTest {
    private final TrainingMapper mapper = mock(TrainingMapper.class);
    private final TrainingDeliveryEvidenceProvider provider = new TrainingDeliveryEvidenceProvider(mapper);
    private TrainingDO confirmed() {
        var row = new TrainingDO(); row.setId(5L); row.setTenantId(1L); row.setProjectId(7L);
        row.setStatus(TrainingStatusEnum.CONFIRMED.getStatus());
        when(mapper.selectById(5L)).thenReturn(row); return row;
    }
    @Test void rejectsConfirmedTrainingFromAnotherProjectInSameTenant() {
        confirmed(); assertThrows(BusinessContractException.class,
            () -> provider.validateCurrent(1L,8L,"5",null));
    }
    @Test void rejectsDifferentTenant() {
        confirmed(); assertThrows(BusinessContractException.class,
            () -> provider.validateCurrent(2L,7L,"5",null));
    }
    @Test void acceptsExactConfirmedTrainingWithoutInventedRevision() {
        confirmed(); assertDoesNotThrow(() -> provider.validateCurrent(1L,7L,"5",null));
    }
    @Test void rejectsRevisionForUnversionedTraining() {
        confirmed(); assertThrows(BusinessContractException.class,
            () -> provider.validateCurrent(1L,7L,"5",1L));
    }
    @Test void rejectsUnconfirmedTraining() {
        confirmed().setStatus(TrainingStatusEnum.DRAFT.getStatus());
        assertThrows(BusinessContractException.class, () -> provider.validateCurrent(1L,7L,"5",null));
    }
}
