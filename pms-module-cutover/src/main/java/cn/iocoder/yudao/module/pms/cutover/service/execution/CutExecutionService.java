package cn.iocoder.yudao.module.pms.cutover.service.execution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.execution.CutExecutionDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 割接执行 Service 接口（FR-CUT-011 / FR-CUT-012）。
 */
public interface CutExecutionService {

    Long createCutExecution(@Valid CutExecutionSaveReqVO createReqVO);

    void updateCutExecution(@Valid CutExecutionSaveReqVO updateReqVO);

    void deleteCutExecution(Long id);

    CutExecutionDO getCutExecution(Long id);

    CutExecutionDO validateCutExecutionExists(Long id);

    PageResult<CutExecutionDO> getCutExecutionPage(CutExecutionPageReqVO pageReqVO);

    List<CutExecutionDO> getCutExecutionListByTask(Long taskId);

    /**
     * 开始执行（0待执行 → 1执行中）
     */
    void start(Long id);

    /**
     * 通过（1执行中 → 2已通过）
     */
    void pass(Long id);

    /**
     * 失败（1执行中 → 3失败）
     */
    void fail(Long id);

    /**
     * 回退（1执行中 → 4已回退）
     */
    void rollback(Long id);
}
