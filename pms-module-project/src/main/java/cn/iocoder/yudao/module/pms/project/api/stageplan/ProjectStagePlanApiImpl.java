package cn.iocoder.yudao.module.pms.project.api.stageplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/** 阶段计划契约实现；写路径仅允许修改计划起止日期字段。 */
@Service
@Validated
@RequiredArgsConstructor
public class ProjectStagePlanApiImpl implements ProjectStagePlanApi {
    private final ProjectPhaseMapper projectPhaseMapper;
    private final ProjectMasterMapper projectMasterMapper;

    @Override
    public List<StagePlanFact> listStages(Long tenantId, Long projectId) {
        requireProject(tenantId, projectId);
        return projectPhaseMapper.selectListByProjectId(projectId).stream()
                .map(ProjectStagePlanApiImpl::toFact)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyPlanDates(Long tenantId, Long projectId, List<StagePlanDate> dates) {
        requireProject(tenantId, projectId);
        if (dates == null || dates.isEmpty()) return 0;
        int updated = 0;
        for (StagePlanDate date : dates) {
            if (date == null || date.stageId() == null) continue;
            ProjectPhaseDO update = new ProjectPhaseDO();
            update.setId(date.stageId());
            update.setPlanStartTime(date.planStartTime() == null ? null : date.planStartTime().atStartOfDay());
            update.setPlanEndTime(date.planEndTime() == null ? null : date.planEndTime().atStartOfDay());
            updated += projectPhaseMapper.updateById(update);
        }
        return updated;
    }

    private static StagePlanFact toFact(ProjectPhaseDO phase) {
        return new StagePlanFact(phase.getId(), phase.getCode(), phase.getName(), phase.getSort(),
                phase.getSuggestedStartTime() == null ? null : phase.getSuggestedStartTime().toLocalDate(),
                phase.getSuggestedEndTime() == null ? null : phase.getSuggestedEndTime().toLocalDate(),
                phase.getPlanStartTime() == null ? null : phase.getPlanStartTime().toLocalDate(),
                phase.getPlanEndTime() == null ? null : phase.getPlanEndTime().toLocalDate(),
                phase.getStatus() == null ? null : String.valueOf(phase.getStatus()), phase.getVersion());
    }

    private void requireProject(Long tenantId, Long projectId) {
        if (tenantId == null || projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("STAGE_PLAN_PROJECT_INVALID");
        }
        var project = projectMasterMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new IllegalArgumentException("STAGE_PLAN_PROJECT_INVALID");
        }
    }
}
