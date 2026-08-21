package cn.iocoder.yudao.module.pms.project.service.schedulebackward;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardItemDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 工期倒排 Service 接口（FR-PROJ-018）。
 * <p>
 * 承载工期倒排记录 CRUD、按目标日期逆序计算阶段计划日期、合理性校验与应用到项目阶段。
 */
public interface ScheduleBackwardService {

    /**
     * 创建工期倒排记录（草稿状态）。
     */
    Long createScheduleBackward(@Valid ScheduleBackwardSaveReqVO createReqVO);

    /**
     * 更新工期倒排记录（仅草稿/已驳回可改）。
     */
    void updateScheduleBackward(@Valid ScheduleBackwardSaveReqVO updateReqVO);

    /**
     * 删除工期倒排记录及其明细。
     */
    void deleteScheduleBackward(Long id);

    /**
     * 查询倒排记录详情。
     */
    ScheduleBackwardDO getScheduleBackward(Long id);

    /**
     * 分页查询倒排记录。
     */
    PageResult<ScheduleBackwardDO> getScheduleBackwardPage(ScheduleBackwardPageReqVO pageReqVO);

    /**
     * 查询倒排阶段明细列表。
     */
    List<ScheduleBackwardItemDO> getScheduleBackwardItems(Long backwardId);

    /**
     * 计算倒排：按项目阶段模板逆序，从目标完工日期往前推算每个阶段计划日期，
     * 校验合理性（不早于今天、不晚于建议期限、阶段间无冲突），给出冲突原因。
     * 直签项目阶段间紧凑排列；非直签阶段间有缓冲。
     *
     * @param id 倒排记录编号
     * @return 计算后的阶段明细列表
     */
    List<ScheduleBackwardItemDO> calculateScheduleBackward(Long id);

    /**
     * 应用倒排结果到项目阶段：将计算结果更新到 proj_project_phase 的计划开始/结束时间。
     * 存在冲突时不允许应用。
     *
     * @param id 倒排记录编号
     */
    void applyScheduleBackward(Long id);

}
