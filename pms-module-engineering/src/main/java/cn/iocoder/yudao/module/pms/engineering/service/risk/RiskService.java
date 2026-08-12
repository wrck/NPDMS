package cn.iocoder.yudao.module.pms.engineering.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskHandleReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.risk.RiskDO;

/**
 * PMS 单机风险 Service 接口（FR-ENG-008）。
 * <p>
 * 状态流转：0 草稿 → 1 已识别 → 2 已确认 → 3 已同步CRM → 4 已关闭。
 */
public interface RiskService {

    /**
     * 创建风险项
     */
    Long createRisk(RiskSaveReqVO createReqVO);

    /**
     * 更新风险项（仅草稿/已识别状态可编辑）
     */
    void updateRisk(RiskSaveReqVO updateReqVO);

    /**
     * 删除风险项（仅草稿状态可删除）
     */
    void deleteRisk(Long id);

    /**
     * 查询风险项详情
     */
    RiskDO getRisk(Long id);

    /**
     * 校验风险项存在，不存在则抛异常
     */
    RiskDO validateRiskExists(Long id);

    /**
     * 分页查询
     */
    PageResult<RiskDO> getRiskPage(RiskPageReqVO pageReqVO);

    /**
     * 确认风险（0 草稿/1 已识别 → 2 已确认）
     */
    void confirmRisk(RiskHandleReqVO reqVO);

    /**
     * 同步CRM（2 已确认 → 3 已同步CRM）
     */
    void syncCrm(Long id);

    /**
     * 关闭风险（3 已同步CRM → 4 已关闭）
     */
    void closeRisk(RiskHandleReqVO reqVO);
}
