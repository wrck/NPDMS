package cn.iocoder.yudao.module.pms.project.service.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;

/**
 * PMS 客户 Service 实现类
 */
@Service
@Validated
public class CustomerServiceImpl implements CustomerService {

    @Resource
    private CustomerMapper customerMapper;

    @Override
    public Long createCustomer(CustomerSaveReqVO createReqVO) {
        // 校验编码唯一
        validateCustomerCodeUnique(null, createReqVO.getCode());
        // 插入客户
        CustomerDO customer = BeanUtils.toBean(createReqVO, CustomerDO.class);
        customerMapper.insert(customer);
        return customer.getId();
    }

    @Override
    public void updateCustomer(CustomerSaveReqVO updateReqVO) {
        // 校验存在
        validateCustomerExists(updateReqVO.getId());
        // 校验编码唯一
        validateCustomerCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 更新客户
        CustomerDO updateObj = BeanUtils.toBean(updateReqVO, CustomerDO.class);
        customerMapper.updateById(updateObj);
    }

    @Override
    public void deleteCustomer(Long id) {
        // 校验存在
        validateCustomerExists(id);
        // 删除客户
        customerMapper.deleteById(id);
    }

    @Override
    public CustomerDO getCustomer(Long id) {
        return customerMapper.selectById(id);
    }

    @Override
    public PageResult<CustomerDO> getCustomerPage(CustomerPageReqVO pageReqVO) {
        return customerMapper.selectPage(pageReqVO);
    }

    private void validateCustomerExists(Long id) {
        if (id == null) {
            return;
        }
        if (customerMapper.selectById(id) == null) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
    }

    private void validateCustomerCodeUnique(Long id, String code) {
        CustomerDO customer = customerMapper.selectByCode(code);
        if (customer == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的客户
        if (id == null) {
            throw exception(CUSTOMER_CODE_DUPLICATE);
        }
        if (!customer.getId().equals(id)) {
            throw exception(CUSTOMER_CODE_DUPLICATE);
        }
    }

}
