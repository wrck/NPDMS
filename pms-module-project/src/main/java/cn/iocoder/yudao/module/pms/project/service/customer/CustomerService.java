package cn.iocoder.yudao.module.pms.project.service.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;

/**
 * PMS 客户 Service 接口
 */
public interface CustomerService {

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
