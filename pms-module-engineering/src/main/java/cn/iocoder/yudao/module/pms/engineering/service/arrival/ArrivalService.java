package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;

import jakarta.validation.Valid;

/**
 * PMS 到货签收 Service 接口（FR-ENG-021）。
 * <p>
 * 状态流转：0 待签收 → 1 已签收 / 2 异常。
 */
public interface ArrivalService {

    Long createArrival(@Valid ArrivalSaveReqVO createReqVO);

    void updateArrival(@Valid ArrivalSaveReqVO updateReqVO);

    void deleteArrival(Long id);

    ArrivalDO getArrival(Long id);

    PageResult<ArrivalDO> getArrivalPage(ArrivalPageReqVO pageReqVO);

    /**
     * 签收：待签收(0) → 已签收(1)
     */
    void signArrival(Long id);

    /**
     * 标记异常：待签收(0) → 异常(2)
     */
    void markAbnormal(Long id);
}
