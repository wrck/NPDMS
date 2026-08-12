package cn.iocoder.yudao.module.pms.project.service.servicelevel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.servicelevel.CustomerServiceLevelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_SERVICE_LEVEL_CUSTOMER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_SERVICE_LEVEL_NOT_EXISTS;

/**
 * PMS 客户服务等级 Service 实现类
 */
@Service
@Validated
public class CustomerServiceLevelServiceImpl implements CustomerServiceLevelService {

    @Resource
    private CustomerServiceLevelMapper customerServiceLevelMapper;
    @Resource
    private CustomerMapper customerMapper;

    @Override
    public Long createCustomerServiceLevel(CustomerServiceLevelSaveReqVO createReqVO) {
        // 校验客户存在
        validateCustomerExists(createReqVO.getCustomerId());
        // 插入服务等级
        CustomerServiceLevelDO serviceLevel = BeanUtils.toBean(createReqVO, CustomerServiceLevelDO.class);
        customerServiceLevelMapper.insert(serviceLevel);
        return serviceLevel.getId();
    }

    @Override
    public void updateCustomerServiceLevel(CustomerServiceLevelSaveReqVO updateReqVO) {
        // 校验存在
        validateCustomerServiceLevelExists(updateReqVO.getId());
        // 校验客户存在
        validateCustomerExists(updateReqVO.getCustomerId());
        // 更新服务等级
        CustomerServiceLevelDO updateObj = BeanUtils.toBean(updateReqVO, CustomerServiceLevelDO.class);
        customerServiceLevelMapper.updateById(updateObj);
    }

    @Override
    public void deleteCustomerServiceLevel(Long id) {
        // 校验存在
        validateCustomerServiceLevelExists(id);
        // 删除服务等级
        customerServiceLevelMapper.deleteById(id);
    }

    @Override
    public CustomerServiceLevelDO getCustomerServiceLevel(Long id) {
        return customerServiceLevelMapper.selectById(id);
    }

    @Override
    public PageResult<CustomerServiceLevelDO> getCustomerServiceLevelPage(CustomerServiceLevelPageReqVO pageReqVO) {
        return customerServiceLevelMapper.selectPage(pageReqVO);
    }

    private void validateCustomerServiceLevelExists(Long id) {
        if (id == null) {
            return;
        }
        if (customerServiceLevelMapper.selectById(id) == null) {
            throw exception(CUSTOMER_SERVICE_LEVEL_NOT_EXISTS);
        }
    }

    private void validateCustomerExists(Long customerId) {
        if (customerId == null) {
            return;
        }
        CustomerDO customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw exception(CUSTOMER_SERVICE_LEVEL_CUSTOMER_NOT_EXISTS);
        }
    }

}
