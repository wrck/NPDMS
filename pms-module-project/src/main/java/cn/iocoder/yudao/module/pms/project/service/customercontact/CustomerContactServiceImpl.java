package cn.iocoder.yudao.module.pms.project.service.customercontact;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customercontact.CustomerContactDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customercontact.CustomerContactMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_CONTACT_CUSTOMER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_CONTACT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_CONTACT_PRIMARY_DUPLICATE;

/**
 * PMS 客户联系人 Service 实现类
 */
@Service
@Validated
public class CustomerContactServiceImpl implements CustomerContactService {

    @Resource
    private CustomerContactMapper customerContactMapper;

    @Resource
    private CustomerMapper customerMapper;

    @Override
    public Long createCustomerContact(CustomerContactSaveReqVO createReqVO) {
        // 校验客户存在
        validateCustomerExists(createReqVO.getCustomerId());
        // 校验主联系人唯一（启用状态下，每个客户仅允许一个主联系人）
        validatePrimaryFlagUnique(null, createReqVO.getCustomerId(),
                Boolean.TRUE.equals(createReqVO.getPrimaryFlag()), createReqVO.getStatus());
        // 插入联系人
        CustomerContactDO contact = BeanUtils.toBean(createReqVO, CustomerContactDO.class);
        customerContactMapper.insert(contact);
        return contact.getId();
    }

    @Override
    public void updateCustomerContact(CustomerContactSaveReqVO updateReqVO) {
        // 校验存在
        validateCustomerContactExists(updateReqVO.getId());
        // 校验客户存在
        validateCustomerExists(updateReqVO.getCustomerId());
        // 校验主联系人唯一
        validatePrimaryFlagUnique(updateReqVO.getId(), updateReqVO.getCustomerId(),
                Boolean.TRUE.equals(updateReqVO.getPrimaryFlag()), updateReqVO.getStatus());
        // 更新联系人
        CustomerContactDO updateObj = BeanUtils.toBean(updateReqVO, CustomerContactDO.class);
        customerContactMapper.updateById(updateObj);
    }

    @Override
    public void deleteCustomerContact(Long id) {
        // 校验存在
        validateCustomerContactExists(id);
        // 删除联系人
        customerContactMapper.deleteById(id);
    }

    @Override
    public CustomerContactDO getCustomerContact(Long id) {
        return customerContactMapper.selectById(id);
    }

    @Override
    public PageResult<CustomerContactDO> getCustomerContactPage(CustomerContactPageReqVO pageReqVO) {
        return customerContactMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CustomerContactDO> getContactListByCustomerId(Long customerId) {
        return customerContactMapper.selectListByCustomerId(customerId);
    }

    private void validateCustomerContactExists(Long id) {
        if (id == null) {
            return;
        }
        if (customerContactMapper.selectById(id) == null) {
            throw exception(CUSTOMER_CONTACT_NOT_EXISTS);
        }
    }

    private void validateCustomerExists(Long customerId) {
        if (customerId == null) {
            return;
        }
        CustomerDO customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw exception(CUSTOMER_CONTACT_CUSTOMER_NOT_EXISTS);
        }
    }

    /**
     * 校验主联系人唯一性：
     * 仅当 primaryFlag=true 且 status=0(启用) 时触发校验，避免与已存在的主联系人冲突。
     * 数据库通过 active_primary_customer_id 生成列 + 唯一索引兜底。
     */
    private void validatePrimaryFlagUnique(Long id, Long customerId, boolean primaryFlag, Integer status) {
        if (!primaryFlag || status == null || status != 0) {
            return;
        }
        CustomerContactDO existing = customerContactMapper.selectActivePrimaryByCustomerId(customerId);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(CUSTOMER_CONTACT_PRIMARY_DUPLICATE);
        }
    }

}
