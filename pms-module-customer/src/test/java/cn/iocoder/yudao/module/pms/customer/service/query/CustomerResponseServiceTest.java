package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.MASKED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerResponseServiceTest {

    private final CustomerResponseService service =
            new CustomerResponseService(new CustomerFieldMaskingService());

    @Test
    void pageAppliesSameContactMaskingRuleToEveryCustomer() {
        CustomerMasterDO customer = customer();

        PageResult<CustomerRespVO> result = service.page(
                new PageResult<>(List.of(customer), 1L), MASKED);

        assertEquals("138****5678", result.getList().getFirst().getContactPhone());
        assertEquals("e****@gmail.com", result.getList().getFirst().getContactEmail());
    }

    @Test
    void detailAppliesContactMaskingRule() {
        CustomerRespVO result = service.detail(customer(), MASKED);

        assertEquals("138****5678", result.getContactPhone());
        assertEquals("e****@gmail.com", result.getContactEmail());
    }

    private CustomerMasterDO customer() {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(100L);
        customer.setContactPhone("13812345678");
        customer.setContactEmail("example@gmail.com");
        return customer;
    }
}
