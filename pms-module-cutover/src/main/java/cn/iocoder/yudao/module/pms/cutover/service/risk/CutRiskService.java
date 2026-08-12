package cn.iocoder.yudao.module.pms.cutover.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 割接风险 Service 接口（FR-CUT-004 / FR-CUT-006）。
 */
public interface CutRiskService {

    Long createCutRisk(@Valid CutRiskSaveReqVO createReqVO);

    void updateCutRisk(@Valid CutRiskSaveReqVO updateReqVO);

    void deleteCutRisk(Long id);

    CutRiskDO getCutRisk(Long id);

    CutRiskDO validateCutRiskExists(Long id);

    PageResult<CutRiskDO> getCutRiskPage(CutRiskPageReqVO pageReqVO);

    List<CutRiskDO> getCutRiskListByTask(Long taskId);

    /**
     * 开始处理（0待处理 → 1处理中）
     */
    void startProcess(Long id);

    /**
     * 闭环（1处理中 → 2已闭环）
     */
    void close(Long id);

    /**
     * 挂起（0待处理/1处理中 → 3已挂起）
     */
    void suspend(Long id);
}
