package cn.iocoder.yudao.module.pms.project.service.batchchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeItemDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 团队批量变更 Service 接口（FR-PROJ-014）。
 * <p>
 * 承载批量变更批次 CRUD、明细生成、逐条执行与状态汇总。
 */
public interface TeamBatchChangeService {

    /**
     * 创建批量变更批次。校验源/目标用户不同，按范围生成明细条目（状态待处理）。
     */
    Long createBatchChange(@Valid TeamBatchChangeSaveReqVO createReqVO);

    /**
     * 更新批量变更批次（仅草稿状态可改）。
     */
    void updateBatchChange(@Valid TeamBatchChangeSaveReqVO updateReqVO);

    /**
     * 删除批量变更批次及其明细。
     */
    void deleteBatchChange(Long id);

    /**
     * 查询批次详情。
     */
    TeamBatchChangeDO getBatchChange(Long id);

    /**
     * 分页查询批次。
     */
    PageResult<TeamBatchChangeDO> getBatchChangePage(TeamBatchChangePageReqVO pageReqVO);

    /**
     * 查询批次明细列表。
     */
    List<TeamBatchChangeItemDO> getBatchChangeItems(Long batchId);

    /**
     * 执行批量变更：逐条处理明细，更新团队成员 user_id，记录成功/失败，汇总批次状态。
     * 部分失败时批次状态为部分成功(2)，逐条返回结果。
     *
     * @param batchId 批次编号
     * @return 处理后的明细列表
     */
    List<TeamBatchChangeItemDO> executeBatchChange(Long batchId);

}
