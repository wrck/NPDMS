package cn.iocoder.yudao.module.pms.service.service.srvrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleDO;

/**
 * 巡检规则 Service 接口
 */
public interface SrvRuleService {

    /**
     * 创建巡检规则
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvRule(SrvRuleSaveReqVO createReqVO);

    /**
     * 更新巡检规则
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvRule(SrvRuleSaveReqVO updateReqVO);

    /**
     * 删除巡检规则
     *
     * @param id 编号
     */
    void deleteSrvRule(Long id);

    /**
     * 获得巡检规则分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvRuleDO> getSrvRulePage(SrvRulePageReqVO pageReqVO);

    /**
     * 获得巡检规则
     *
     * @param id 编号
     * @return 巡检规则
     */
    SrvRuleDO getSrvRule(Long id);

    /**
     * 发布巡检规则
     *
     * @param id 编号
     */
    void publishSrvRule(Long id);

    /**
     * 停用巡检规则
     *
     * @param id 编号
     */
    void disableSrvRule(Long id);

}
