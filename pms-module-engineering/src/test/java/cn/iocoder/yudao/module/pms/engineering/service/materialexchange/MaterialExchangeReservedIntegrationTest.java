package cn.iocoder.yudao.module.pms.engineering.service.materialexchange;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialExchangeReservedIntegrationTest {
    private final MaterialExchangeMapper mapper = mock(MaterialExchangeMapper.class);
    private final MaterialExchangeServiceImpl service = new MaterialExchangeServiceImpl();

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "materialExchangeMapper", mapper);
    }

    @Test void reservedEndpointDoesNotSimulateSendingOrReceivingAnOrder() {
        for (String claimedOrder : new String[]{null, "CRM-CALLER-SUPPLIED"}) {
            MaterialExchangeDO row = new MaterialExchangeDO();
            row.setId(1L); row.setStatus(3); row.setVersion(7); row.setCrmPushStatus("PENDING");
            when(mapper.selectById(1L)).thenReturn(row);

            ServiceException error = assertThrows(ServiceException.class, () -> service.pushToCrm(1L, claimedOrder));

            assertEquals(MATERIAL_EXCH_CRM_NOT_CONNECTED.getCode(), error.getCode());
            assertEquals("PENDING", row.getCrmPushStatus());
            assertEquals(7, row.getVersion());
            assertEquals(3, row.getStatus());
            assertNull(row.getCrmOrderNo());
            assertNull(row.getCrmPushTime());
        }
        verify(mapper, times(2)).selectById(1L);
        verifyNoMoreInteractions(mapper);
    }

    @Test void historicalCrmEvidenceIsNotRewrittenWhenIntegrationIsPaused() {
        MaterialExchangeDO row = new MaterialExchangeDO();
        row.setId(2L); row.setCrmPushStatus("RECEIVED"); row.setCrmOrderNo("CRM-HISTORICAL");
        LocalDateTime sentAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        row.setCrmPushTime(sentAt); row.setVersion(4);
        when(mapper.selectById(2L)).thenReturn(row);

        ServiceException error = assertThrows(ServiceException.class, () -> service.pushToCrm(2L, "OTHER"));

        assertEquals(MATERIAL_EXCH_CRM_ALREADY_PUSHED.getCode(), error.getCode());
        assertEquals("RECEIVED", row.getCrmPushStatus());
        assertEquals("CRM-HISTORICAL", row.getCrmOrderNo());
        assertEquals(sentAt, row.getCrmPushTime());
        assertEquals(4, row.getVersion());
        verify(mapper).selectById(2L);
        verifyNoMoreInteractions(mapper);
    }

    @Test void missingApplicationStillFailsWithoutAnyWrite() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.pushToCrm(99L, null));
        assertEquals(MATERIAL_EXCH_NOT_EXISTS.getCode(), error.getCode());
        verify(mapper).selectById(99L);
        verifyNoMoreInteractions(mapper);
    }
}
