package cn.iocoder.yudao.module.pms.project.service.schedulebackward;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardItemDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.schedulebackward.ScheduleBackwardItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.schedulebackward.ScheduleBackwardMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/**
 * PMS 工期倒排 Service 实现（FR-PROJ-018）。
 * <p>
 * 按项目阶段 sort 逆序，从目标完工日期往前推算每个阶段计划开始/结束日期。
 * 直签项目（DIRECT）阶段间紧凑排列；非直签（INDIRECT）阶段间预留缓冲日。
 * 校验：计划开始不早于今天、不晚于建议最晚日期、阶段间无重叠，给出冲突原因。
 */
@Service
@Validated
@Slf4j
public class ScheduleBackwardServiceImpl implements ScheduleBackwardService {

    /** 倒排状态：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 倒排状态：已计算 */
    private static final int STATUS_CALCULATED = 1;
    /** 倒排状态：已应用 */
    private static final int STATUS_APPLIED = 2;
    /** 倒排状态：已驳回 */
    private static final int STATUS_REJECTED = 3;

    /** 默认阶段工期（天），当阶段无建议开始/结束时间时使用 */
    private static final int DEFAULT_PHASE_DURATION_DAYS = 7;
    /** 非直签项目阶段间缓冲天数 */
    private static final int INDIRECT_BUFFER_DAYS = 2;

    @Resource
    private ScheduleBackwardMapper scheduleBackwardMapper;
    @Resource
    private ScheduleBackwardItemMapper scheduleBackwardItemMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    public Long createScheduleBackward(ScheduleBackwardSaveReqVO createReqVO) {
        // 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        ScheduleBackwardDO backward = BeanUtils.toBean(createReqVO, ScheduleBackwardDO.class);
        backward.setStatus(STATUS_DRAFT);
        scheduleBackwardMapper.insert(backward);
        return backward.getId();
    }

    @Override
    public void updateScheduleBackward(ScheduleBackwardSaveReqVO updateReqVO) {
        ScheduleBackwardDO existing = validateScheduleBackwardExists(updateReqVO.getId());
        // 仅草稿/已驳回可改
        if (existing.getStatus() != null
                && existing.getStatus() != STATUS_DRAFT && existing.getStatus() != STATUS_REJECTED) {
            throw exception(SCHEDULE_BACKWARD_STATUS_INVALID);
        }
        validateProjectExists(updateReqVO.getProjectId());
        ScheduleBackwardDO update = BeanUtils.toBean(updateReqVO, ScheduleBackwardDO.class);
        scheduleBackwardMapper.updateById(update);
    }

    @Override
    @Transactional
    public void deleteScheduleBackward(Long id) {
        validateScheduleBackwardExists(id);
        scheduleBackwardItemMapper.deleteByBackwardId(id);
        scheduleBackwardMapper.deleteById(id);
    }

    @Override
    public ScheduleBackwardDO getScheduleBackward(Long id) {
        return scheduleBackwardMapper.selectById(id);
    }

    @Override
    public PageResult<ScheduleBackwardDO> getScheduleBackwardPage(ScheduleBackwardPageReqVO pageReqVO) {
        return scheduleBackwardMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ScheduleBackwardItemDO> getScheduleBackwardItems(Long backwardId) {
        return scheduleBackwardItemMapper.selectListByBackwardId(backwardId);
    }

    @Override
    @Transactional
    public List<ScheduleBackwardItemDO> calculateScheduleBackward(Long id) {
        ScheduleBackwardDO backward = validateScheduleBackwardExists(id);
        // 仅草稿/已计算/已驳回可计算（支持重算）
        if (backward.getStatus() != null && backward.getStatus() == STATUS_APPLIED) {
            throw exception(SCHEDULE_BACKWARD_STATUS_INVALID);
        }
        // 1. 查询项目阶段（按 sort 升序）
        List<ProjectPhaseDO> phases = projectPhaseMapper.selectListByProjectId(backward.getProjectId());
        if (phases.isEmpty()) {
            throw exception(SCHEDULE_BACKWARD_NO_PHASES);
        }
        // 2. 逆序推算：从最后一个阶段（目标完工日期）往前推
        LocalDate today = LocalDate.now();
        LocalDate cursor = backward.getTargetDate();
        boolean isIndirect = "INDIRECT".equalsIgnoreCase(backward.getProjectType());
        // 逆序处理，结果按 sort 升序保存
        List<ScheduleBackwardItemDO> computed = new ArrayList<>(phases.size());
        List<ProjectPhaseDO> reversed = new ArrayList<>(phases);
        Collections.reverse(reversed);
        LocalDate prevPhaseStart = null;
        for (ProjectPhaseDO phase : reversed) {
            int durationDays = computePhaseDurationDays(phase);
            LocalDate plannedEnd = cursor;
            LocalDate plannedStart = plannedEnd.minusDays(Math.max(durationDays - 1, 0));
            LocalDate recommendedLatest = toLocalDate(phase.getSuggestedEndTime());
            // 3. 校验合理性
            List<String> reasons = new ArrayList<>();
            if (plannedStart.isBefore(today)) {
                reasons.add("计划开始日期早于今天");
            }
            if (recommendedLatest != null && plannedEnd.isAfter(recommendedLatest)) {
                reasons.add("计划结束日期晚于建议最晚日期");
            }
            // 阶段间重叠校验：当前阶段开始须晚于前一（sort 更大）阶段的结束
            if (prevPhaseStart != null && !plannedEnd.isBefore(prevPhaseStart)) {
                reasons.add("与相邻阶段存在日期重叠");
            }
            // 4. 构造明细
            ScheduleBackwardItemDO item = new ScheduleBackwardItemDO();
            item.setBackwardId(id);
            item.setPhaseId(phase.getId());
            item.setPhaseName(phase.getName());
            item.setPlannedStartDate(plannedStart);
            item.setPlannedEndDate(plannedEnd);
            item.setRecommendedLatestDate(recommendedLatest);
            item.setSort(phase.getSort() != null ? phase.getSort() : 0);
            if (reasons.isEmpty()) {
                item.setHasConflict(false);
                item.setConflictReason(null);
            } else {
                item.setHasConflict(true);
                item.setConflictReason(String.join("；", reasons));
            }
            computed.add(item);
            // 5. 推进游标：直签紧凑排列（下个阶段结束 = 本阶段开始 - 1 天）；
            //    非直签预留缓冲（下个阶段结束 = 本阶段开始 - 1 - 缓冲天数）
            cursor = plannedStart.minusDays(1);
            if (isIndirect) {
                cursor = cursor.minusDays(INDIRECT_BUFFER_DAYS);
            }
            prevPhaseStart = plannedStart;
        }
        // 6. 持久化：先删旧明细，再插入新明细（按 sort 升序）
        scheduleBackwardItemMapper.deleteByBackwardId(id);
        // computed 当前为逆序，反转回 sort 升序后插入
        Collections.reverse(computed);
        for (ScheduleBackwardItemDO item : computed) {
            scheduleBackwardItemMapper.insert(item);
        }
        // 7. 汇总冲突并更新批次状态为已计算
        String conflictSummary = buildConflictSummary(computed);
        ScheduleBackwardDO update = new ScheduleBackwardDO();
        update.setId(id);
        update.setStatus(STATUS_CALCULATED);
        update.setConflictSummary(conflictSummary);
        update.setVersion(backward.getVersion());
        scheduleBackwardMapper.updateById(update);
        return computed;
    }

    @Override
    @Transactional
    public void applyScheduleBackward(Long id) {
        ScheduleBackwardDO backward = validateScheduleBackwardExists(id);
        // 仅已计算状态可应用
        if (backward.getStatus() == null || backward.getStatus() != STATUS_CALCULATED) {
            throw exception(SCHEDULE_BACKWARD_STATUS_INVALID);
        }
        List<ScheduleBackwardItemDO> items = scheduleBackwardItemMapper.selectListByBackwardId(id);
        // 存在冲突不允许应用
        boolean hasConflict = items.stream().anyMatch(ScheduleBackwardItemDO::getHasConflict);
        if (hasConflict) {
            throw exception(SCHEDULE_BACKWARD_HAS_CONFLICT);
        }
        // 将计算结果更新到 pms_project_phase 的计划开始/结束时间
        for (ScheduleBackwardItemDO item : items) {
            if (item.getPhaseId() == null) {
                continue;
            }
            ProjectPhaseDO update = new ProjectPhaseDO();
            update.setId(item.getPhaseId());
            update.setPlanStartTime(atStartOfDay(item.getPlannedStartDate()));
            update.setPlanEndTime(atStartOfDay(item.getPlannedEndDate()));
            projectPhaseMapper.updateById(update);
        }
        // 更新倒排记录状态为已应用
        ScheduleBackwardDO update = new ScheduleBackwardDO();
        update.setId(id);
        update.setStatus(STATUS_APPLIED);
        update.setVersion(backward.getVersion());
        scheduleBackwardMapper.updateById(update);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 计算阶段工期天数：优先使用建议开始/结束时间差，否则默认 7 天。
     */
    private int computePhaseDurationDays(ProjectPhaseDO phase) {
        if (phase.getSuggestedStartTime() != null && phase.getSuggestedEndTime() != null) {
            long days = ChronoUnit.DAYS.between(
                    phase.getSuggestedStartTime().toLocalDate(),
                    phase.getSuggestedEndTime().toLocalDate()) + 1;
            if (days > 0) {
                return (int) days;
            }
        }
        return DEFAULT_PHASE_DURATION_DAYS;
    }

    private LocalDate toLocalDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private LocalDateTime atStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private String buildConflictSummary(List<ScheduleBackwardItemDO> items) {
        List<String> conflicts = new ArrayList<>();
        for (ScheduleBackwardItemDO item : items) {
            if (Boolean.TRUE.equals(item.getHasConflict())) {
                conflicts.add("阶段【" + item.getPhaseName() + "】" + item.getConflictReason());
            }
        }
        return conflicts.isEmpty() ? null : String.join("；", conflicts);
    }

    private void validateProjectExists(Long projectId) {
        if (projectId == null) {
            return;
        }
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
    }

    private ScheduleBackwardDO validateScheduleBackwardExists(Long id) {
        ScheduleBackwardDO backward = scheduleBackwardMapper.selectById(id);
        if (backward == null) {
            throw exception(SCHEDULE_BACKWARD_NOT_EXISTS);
        }
        return backward;
    }

}
