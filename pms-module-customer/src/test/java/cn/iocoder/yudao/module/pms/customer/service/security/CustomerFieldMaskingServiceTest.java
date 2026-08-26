package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.HIDDEN;
import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.MASKED;
import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomerFieldMaskingServiceTest {

    private final CustomerFieldMaskingService service = new CustomerFieldMaskingService();

    @Test
    void rawAccessReturnsOriginalContactValues() {
        CustomerRespVO response = response();

        service.apply(response, RAW);

        assertEquals("13812345678", response.getContactPhone());
        assertEquals("example@gmail.com", response.getContactEmail());
    }

    @Test
    void maskedAccessReturnsMaskedContactValues() {
        CustomerRespVO response = response();

        service.apply(response, MASKED);

        assertEquals("138****5678", response.getContactPhone());
        assertEquals("e****@gmail.com", response.getContactEmail());
    }

    @Test
    void hiddenAccessRemovesContactValues() {
        CustomerRespVO response = response();

        service.apply(response, HIDDEN);

        assertNull(response.getContactPhone());
        assertNull(response.getContactEmail());
    }

    private CustomerRespVO response() {
        CustomerRespVO response = new CustomerRespVO();
        response.setContactPhone("13812345678");
        response.setContactEmail("example@gmail.com");
        return response;
    }
}
