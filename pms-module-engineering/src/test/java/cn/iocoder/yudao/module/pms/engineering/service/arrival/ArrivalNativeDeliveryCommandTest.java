package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ArrivalNativeDeliveryCommandTest {
    ArrivalMapper mapper = mock(ArrivalMapper.class);
    ArrivalDeliveryRegistration registration = mock(ArrivalDeliveryRegistration.class);
    ArrivalServiceImpl service = new ArrivalServiceImpl();
    ArrivalDO row;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service, "arrivalMapper", mapper);
        ReflectionTestUtils.setField(service, "deliveryRegistration", registration);
        row = new ArrivalDO(); row.setId(9L); row.setTenantId(7L); row.setProjectId(20L);
        row.setVersion(3L); row.setStatus(0);
        when(mapper.selectDeliveryOwnerForUpdate(any())).thenReturn(row);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void deniedDeleteKeepsTheOwnerAndItsMaterialSources() {
        doThrow(new IllegalStateException("scope denied")).when(registration).requireDelete(row);
        assertThrows(IllegalStateException.class, () -> service.deleteArrival(9L));
        verify(mapper, never()).deleteEditable(any());
    }
    @Test void signedOwnerCannotBeDeleted() {
        row.setStatus(1);
        assertThrows(RuntimeException.class, () -> service.deleteArrival(9L));
        verify(mapper, never()).deleteEditable(any());
    }
    @Test void authorizedEditableDeleteStillUsesTheNativeVersionCas() {
        when(mapper.deleteEditable(any())).thenReturn(1);
        service.deleteArrival(9L);
        verify(registration).requireDelete(row);
        verify(mapper).deleteEditable(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalEditableDeleteQuery(9L, 3L));
    }
    @Test void failedFileCollectionPreventsSigningAndResultRegistration() {
        doThrow(new IllegalStateException("material registration failed")).when(registration).registerFiles(row);
        assertThrows(IllegalStateException.class, () -> service.signArrival(9L));
        verify(mapper, never()).updateById(row);
        verify(registration, never()).registerSigned(any());
        assertEquals(0, row.getStatus());
    }
}
