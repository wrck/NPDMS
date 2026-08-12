package cn.iocoder.yudao.module.pms.project.service.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;

import jakarta.validation.Valid;

/**
 * PMS 客户 Service 接口
 */
public interface CustomerService {

    /**
     * 创建客户
     *
     * @param createReqVO 客户信息
     * @return 客户编号
     */
    Long createCustomer(@Valid CustomerSaveReqVO createReqVO);

    /**
     * 更新客户
     *
     * @param updateReqVO 客户信息
     */
    void updateCustomer(@Valid CustomerSaveReqVO updateReqVO);

    /**
     * 删除客户
     *
     * @param id 客户编号
     */
    void deleteCustomer(Long id);

    /**
     * 获得客户
     *
     * @param id 客户编号
     * @return 客户信息
     */
    CustomerDO getCustomer(Long id);

    /**
     * 获得客户分页列表
     *
     * @param pageReqVO 分页条件
     * @return 客户分页列表
     */
    PageResult<CustomerDO> getCustomerPage(CustomerPageReqVO pageReqVO);

}
