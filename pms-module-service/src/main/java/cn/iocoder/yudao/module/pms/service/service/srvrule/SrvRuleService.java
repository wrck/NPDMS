package cn.iocoder.yudao.module.pms.service.service.srvrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleRetiredDO;

/**
 * 巡检规则 Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface SrvRuleService {

    /**
     * 创建巡检规则
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvRuleRetired(SrvRuleSaveReqVO createReqVO);

    /**
     * 更新巡检规则
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvRuleRetired(SrvRuleSaveReqVO updateReqVO);

    /**
     * 删除巡检规则
     *
     * @param id 编号
     */
    void deleteSrvRuleRetired(Long id);

    /**
     * 获得巡检规则分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvRuleRetiredDO> getSrvRulePageRetired(SrvRulePageReqVO pageReqVO);

    /**
     * 获得巡检规则
     *
     * @param id 编号
     * @return 巡检规则
     */
    SrvRuleRetiredDO getSrvRuleRetired(Long id);

    /**
     * 发布巡检规则
     *
     * @param id 编号
     */
    void publishSrvRuleRetired(Long id);

    /**
     * 停用巡检规则
     *
     * @param id 编号
     */
    void disableSrvRuleRetired(Long id);

}
