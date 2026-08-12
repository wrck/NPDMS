package cn.iocoder.yudao.module.pms.cutover.service.observation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.observation.CutObservationDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 稳定观察 Service 接口（FR-CUT-013 / FR-CUT-014）。
 */
public interface CutObservationService {

    Long createCutObservation(@Valid CutObservationSaveReqVO createReqVO);

    void updateCutObservation(@Valid CutObservationSaveReqVO updateReqVO);

    void deleteCutObservation(Long id);

    CutObservationDO getCutObservation(Long id);

    CutObservationDO validateCutObservationExists(Long id);

    PageResult<CutObservationDO> getCutObservationPage(CutObservationPageReqVO pageReqVO);

    List<CutObservationDO> getCutObservationListByTask(Long taskId);

    /**
     * 观察通过（0观察中 → 1已通过）
     */
    void pass(Long id);

    /**
     * 标记异常（0观察中 → 2异常）
     */
    void markAbnormal(Long id);

    /**
     * 归档（1已通过 → 3已归档），归档前校验遗留项已闭环
     */
    void archive(Long id);
}
