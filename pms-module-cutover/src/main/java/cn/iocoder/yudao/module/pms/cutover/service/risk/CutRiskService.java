package cn.iocoder.yudao.module.pms.cutover.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskRetiredDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 割接风险 Service 接口（FR-CUT-004 / FR-CUT-006）。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface CutRiskService {

    Long createCutRiskRetired(@Valid CutRiskSaveReqVO createReqVO);

    void updateCutRiskRetired(@Valid CutRiskSaveReqVO updateReqVO);

    void deleteCutRiskRetired(Long id);

    CutRiskRetiredDO getCutRiskRetired(Long id);

    CutRiskRetiredDO validateCutRiskExistsRetired(Long id);

    PageResult<CutRiskRetiredDO> getCutRiskPageRetired(CutRiskPageReqVO pageReqVO);

    List<CutRiskRetiredDO> getCutRiskListByTaskRetired(Long taskId);

    /**
     * 开始处理（0待处理 → 1处理中）
     */
    void startProcessRetired(Long id);

    /**
     * 闭环（1处理中 → 2已闭环）
     */
    void closeRetired(Long id);

    /**
     * 挂起（0待处理/1处理中 → 3已挂起）
     */
    void suspendRetired(Long id);
}
