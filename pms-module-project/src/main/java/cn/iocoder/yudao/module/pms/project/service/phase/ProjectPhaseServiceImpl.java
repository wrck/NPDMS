package cn.iocoder.yudao.module.pms.project.service.phase;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhasePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhaseSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phasetemplate.ProjectPhaseTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phasetemplate.ProjectPhaseTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import cn.iocoder.yudao.module.pms.project.domain.task.TaskStatusRules;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/**
 * PMS 项目阶段 Service 实现（FR-PROJ-017 / FR-PROJ-016 / FR-PROJ-019）。
 * <p>
 * 阶段顺序通过 {@code sort} 升序控制；开始某阶段前需校验前序阶段已完成或已跳过。
 * 完成门禁包含：项目下全部任务为已完成或已取消、exit_criteria 已记录；超期/临期阶段按 plan_end_time 判定。
 */
@Service
@Validated
@Slf4j
public class ProjectPhaseServiceImpl implements ProjectPhaseService {

    /** 阶段状态：未开始 */
    private static final int PHASE_NOT_STARTED = 0;
    /** 阶段状态：进行中 */
    private static final int PHASE_IN_PROGRESS = 1;
    /** 阶段状态：已完成 */
    private static final int PHASE_COMPLETED = 2;
    /** 阶段状态：已跳过 */
    private static final int PHASE_SKIPPED = 3;

    @Resource
    private ProjectPhaseMapper projectPhaseMapper;
    @Resource
    private ProjectPhaseTemplateMapper projectPhaseTemplateMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectTaskMapper projectTaskMapper;

    @Override
    public Long createPhase(ProjectPhaseSaveReqVO createReqVO) {
        // 1. 校验项目存在
        if (projectMapper.selectById(createReqVO.getProjectId()) == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        // 2. 校验阶段编码项目内唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 3. 校验模板存在（如指定）
        if (createReqVO.getTemplateId() != null) {
            ProjectPhaseTemplateDO template = projectPhaseTemplateMapper.selectById(createReqVO.getTemplateId());
            if (template == null || !Integer.valueOf(0).equals(template.getStatus())) {
                throw exception(PROJECT_PHASE_TEMPLATE_NOT_EXISTS);
            }
        }
        // 4. 写入
        ProjectPhaseDO phase = BeanUtils.toBean(createReqVO, ProjectPhaseDO.class);
        if (phase.getSort() == null) {
            phase.setSort(0);
        }
        if (phase.getStatus() == null) {
            phase.setStatus(PHASE_NOT_STARTED);
        }
        projectPhaseMapper.insert(phase);
        return phase.getId();
    }

    @Override
    public void updatePhase(ProjectPhaseSaveReqVO updateReqVO) {
        // 1. 校验存在
        ProjectPhaseDO existing = validatePhaseExists(updateReqVO.getId());
        // 2. 项目不可变
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())) {
            throw exception(PROJECT_PHASE_NOT_EXISTS);
        }
        // 3. 校验编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 4. 更新（乐观锁由 @Version 自动处理）
        ProjectPhaseDO update = BeanUtils.toBean(updateReqVO, ProjectPhaseDO.class);
        projectPhaseMapper.updateById(update);
    }

    @Override
    public void deletePhase(Long id) {
        // 1. 校验存在
        validatePhaseExists(id);
        // 2. 删除
        projectPhaseMapper.deleteById(id);
    }

    @Override
    public void deletePhaseList(Collection<Long> ids) {
        for (Long id : ids) {
            deletePhase(id);
        }
    }

    @Override
    public ProjectPhaseDO getPhase(Long id) {
        return projectPhaseMapper.selectById(id);
    }

    @Override
    public ProjectPhaseDO validatePhaseExists(Long id) {
        ProjectPhaseDO phase = projectPhaseMapper.selectById(id);
        if (phase == null) {
            throw exception(PROJECT_PHASE_NOT_EXISTS);
        }
        return phase;
    }

    @Override
    public PageResult<ProjectPhaseDO> getPhasePage(ProjectPhasePageReqVO pageReqVO) {
        return projectPhaseMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ProjectPhaseDO> getPhaseListByProjectId(Long projectId) {
        return projectPhaseMapper.selectListByProjectId(projectId);
    }

    @Override
    @Transactional
    public Long instantiateFromTemplate(Long projectId, Long templateId) {
        // 1. 校验项目存在
        if (projectMapper.selectById(projectId) == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        // 2. 校验模板存在并启用
        ProjectPhaseTemplateDO template = projectPhaseTemplateMapper.selectById(templateId);
        if (template == null || !Integer.valueOf(0).equals(template.getStatus())) {
            throw exception(PROJECT_PHASE_TEMPLATE_NOT_EXISTS);
        }
        // 3. 校验项目内无重复 code
        ProjectPhaseDO existing = projectPhaseMapper.selectByProjectAndCode(projectId, template.getCode());
        if (existing != null) {
            throw exception(PROJECT_PHASE_GATE_NOT_PASSED, template.getCode(),
                    "项目内已存在相同编码的阶段【" + template.getCode() + "】");
        }
        // 4. 实例化
        ProjectPhaseDO phase = new ProjectPhaseDO();
        phase.setProjectId(projectId);
        phase.setTemplateId(template.getId());
        phase.setName(template.getName());
        phase.setCode(template.getCode());
        phase.setSort(template.getSort() != null ? template.getSort() : 0);
        phase.setStatus(PHASE_NOT_STARTED);
        phase.setEntryCriteria(template.getEntryCriteria());
        phase.setExitCriteria(template.getExitCriteria());
        phase.setResponsibleRole(template.getResponsibleRole());
        projectPhaseMapper.insert(phase);
        return phase.getId();
    }

    @Override
    public void validateSequence(Long phaseId) {
        ProjectPhaseDO phase = validatePhaseExists(phaseId);
        // 若阶段已完成或已跳过，无需校验前序
        if (phase.getStatus() != null && (phase.getStatus() == PHASE_COMPLETED || phase.getStatus() == PHASE_SKIPPED)) {
            return;
        }
        // 查询同项目下排序靠前的全部阶段，须全部为已完成或已跳过
        List<ProjectPhaseDO> phases = projectPhaseMapper.selectListByProjectId(phase.getProjectId());
        int currentSort = phase.getSort() != null ? phase.getSort() : 0;
        for (ProjectPhaseDO prev : phases) {
            if (Objects.equals(prev.getId(), phase.getId())) {
                continue;
            }
            int prevSort = prev.getSort() != null ? prev.getSort() : 0;
            if (prevSort < currentSort) {
                if (prev.getStatus() == null
                        || (prev.getStatus() != PHASE_COMPLETED && prev.getStatus() != PHASE_SKIPPED)) {
                    throw exception(PROJECT_PHASE_SEQUENCE_INVALID);
                }
            }
        }
    }

    @Override
    public GateCheckResult checkCompletionGate(Long phaseId) {
        ProjectPhaseDO phase = validatePhaseExists(phaseId);
        StringBuilder reason = new StringBuilder();
        long unfinishedTaskCount = 0;
        // 1. 校验项目下全部任务为已完成或已取消
        List<ProjectTaskDO> tasks = projectTaskMapper.selectListByProjectId(phase.getProjectId());
        for (ProjectTaskDO task : tasks) {
            int status = task.getStatus() != null ? task.getStatus() : TaskStatusRules.DRAFT;
            if (!TaskStatusRules.isFinished(status)) {
                unfinishedTaskCount++;
            }
        }
        if (unfinishedTaskCount > 0) {
            reason.append("项目下仍有 ").append(unfinishedTaskCount).append(" 个未完成任务（未完成/未取消）；");
        }
        // 2. 校验 exit_criteria 已记录
        boolean exitCriteriaDocumented = StringUtils.isNotBlank(phase.getExitCriteria());
        if (!exitCriteriaDocumented) {
            reason.append("阶段退出条件（exit_criteria）未记录；");
        }
        // 3. 汇总
        boolean passed = unfinishedTaskCount == 0 && exitCriteriaDocumented;
        String reasonText = passed ? "门禁通过" : reason.toString();
        return new GateCheckResult(passed, unfinishedTaskCount, exitCriteriaDocumented, reasonText);
    }

    @Override
    @Transactional
    public void completePhase(Long phaseId, String gateEvidence, Integer version) {
        // 1. 校验阶段存在
        ProjectPhaseDO phase = validatePhaseExists(phaseId);
        // 2. 执行门禁校验
        GateCheckResult gateResult = checkCompletionGate(phaseId);
        if (!gateResult.isPassed()) {
            throw exception(PROJECT_PHASE_GATE_NOT_PASSED, phase.getName(), gateResult.getReason());
        }
        // 3. 写入实际结束时间、状态、版本
        ProjectPhaseDO update = new ProjectPhaseDO();
        update.setId(phaseId);
        update.setStatus(PHASE_COMPLETED);
        update.setActualEndTime(now());
        update.setVersion(version);
        // 将门禁证据追加到 deviation_reason（保留历史记录）
        if (StringUtils.isNotBlank(gateEvidence)) {
            String existing = phase.getDeviationReason();
            String appended = StringUtils.isBlank(existing)
                    ? gateEvidence
                    : existing + "\n[完成证据] " + gateEvidence;
            update.setDeviationReason(appended);
        }
        projectPhaseMapper.updateById(update);
    }

    @Override
    public List<ProjectPhaseDO> getOverduePhases() {
        return projectPhaseMapper.selectOverdueList(now());
    }

    @Override
    public List<ProjectPhaseDO> getUpcomingPhases(int daysWithin) {
        if (daysWithin < 0) {
            daysWithin = 0;
        }
        LocalDateTime from = now();
        LocalDateTime to = from.plusDays(daysWithin);
        return projectPhaseMapper.selectUpcomingList(from, to);
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        ProjectPhaseDO existing = projectPhaseMapper.selectByProjectAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(PROJECT_PHASE_CODE_DUPLICATE, code);
        }
    }
}
