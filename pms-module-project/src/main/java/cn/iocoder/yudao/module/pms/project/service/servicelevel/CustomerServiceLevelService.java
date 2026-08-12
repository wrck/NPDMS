package cn.iocoder.yudao.module.pms.project.service.servicelevel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import jakarta.validation.Valid;

/**
 * PMS 客户服务等级 Service 接口
 */
public interface CustomerServiceLevelService {

    /**
     * 创建客户服务等级
     *
     * @param createReqVO 服务等级信息
     * @return 服务等级编号
     */
    Long createCustomerServiceLevel(@Valid CustomerServiceLevelSaveReqVO createReqVO);

    /**
     * 更新客户服务等级
     *
     * @param updateReqVO 服务等级信息
     */
    void updateCustomerServiceLevel(@Valid CustomerServiceLevelSaveReqVO updateReqVO);

    /**
     * 删除客户服务等级
     *
     * @param id 服务等级编号
     */
    void deleteCustomerServiceLevel(Long id);

    /**
     * 获得客户服务等级
     *
     * @param id 服务等级编号
     * @return 服务等级信息
     */
    CustomerServiceLevelDO getCustomerServiceLevel(Long id);

    /**
     * 获得客户服务等级分页列表
     *
     * @param pageReqVO 分页条件
     * @return 服务等级分页列表
     */
    PageResult<CustomerServiceLevelDO> getCustomerServiceLevelPage(CustomerServiceLevelPageReqVO pageReqVO);

}
