package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerResponseService {

    private final CustomerFieldMaskingService fieldMaskingService;

    public CustomerResponseService(CustomerFieldMaskingService fieldMaskingService) {
        this.fieldMaskingService = fieldMaskingService;
    }

    public PageResult<CustomerRespVO> page(
            PageResult<CustomerMasterDO> customers,
            CustomerFieldMaskingService.ContactAccess access) {
        List<CustomerRespVO> list = customers.getList().stream()
                .map(customer -> detail(customer, access))
                .toList();
        return new PageResult<>(list, customers.getTotal());
    }

    public CustomerRespVO detail(
            CustomerMasterDO customer,
            CustomerFieldMaskingService.ContactAccess access) {
        CustomerRespVO response = BeanUtils.toBean(customer, CustomerRespVO.class);
        return fieldMaskingService.apply(response, access);
    }
}
