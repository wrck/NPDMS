package cn.iocoder.yudao.module.pms.project.service.customercontact;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customercontact.CustomerContactDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * PMS 客户联系人 Service 接口
 */
public interface CustomerContactService {

    /**
     * 创建客户联系人
     *
     * @param createReqVO 联系人信息
     * @return 联系人编号
     */
    Long createCustomerContact(@Valid CustomerContactSaveReqVO createReqVO);

    /**
     * 更新客户联系人
     *
     * @param updateReqVO 联系人信息
     */
    void updateCustomerContact(@Valid CustomerContactSaveReqVO updateReqVO);

    /**
     * 删除客户联系人
     *
     * @param id 联系人编号
     */
    void deleteCustomerContact(Long id);

    /**
     * 获得客户联系人
     *
     * @param id 联系人编号
     * @return 联系人信息
     */
    CustomerContactDO getCustomerContact(Long id);

    /**
     * 获得客户联系人分页列表
     *
     * @param pageReqVO 分页条件
     * @return 联系人分页列表
     */
    PageResult<CustomerContactDO> getCustomerContactPage(CustomerContactPageReqVO pageReqVO);

    /**
     * 根据客户编号获取联系人列表
     *
     * @param customerId 客户编号
     * @return 联系人列表
     */
    List<CustomerContactDO> getContactListByCustomerId(Long customerId);

}
