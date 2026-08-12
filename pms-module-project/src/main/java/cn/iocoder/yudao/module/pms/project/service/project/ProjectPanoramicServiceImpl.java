package cn.iocoder.yudao.module.pms.project.service.project;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPanoramicRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectProgressRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam.ProjectTeamMemberMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.risk.ProjectRiskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;

/**
 * PMS 项目全景 Service 实现（FR-PROJ-011 / FR-PROJ-021 / FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 聚合项目基本信息、客户信息、阶段汇总、任务汇总、风险汇总与团队成员列表；
 * 总体进度按 60% 任务 + 40% 阶段加权计算。
 */
@Service
@Validated
@Slf4j
public class ProjectPanoramicServiceImpl implements ProjectPanoramicService {

    /**
     * 阶段状态：0 未开始、1 进行中、2 已完成、3 已跳过
     */
    private static final int PHASE_STATUS_NOT_STARTED = 0;
    private static final int PHASE_STATUS_IN_PROGRESS = 1;
    private static final int PHASE_STATUS_COMPLETED = 2;
    private static final int PHASE_STATUS_SKIPPED = 3;

    /**
     * 任务状态：0 草稿、1 待处理、2 进行中、3 受阻、4 待验证、5 已完成、6 已取消
     */
    private static final int TASK_STATUS_IN_PROGRESS = 2;
    private static final int TASK_STATUS_BLOCKED = 3;
    private static final int TASK_STATUS_COMPLETED = 5;

    /**
     * 风险状态：0 已识别、1 处理中、2 已关闭、3 已发生
     */
    private static final int RISK_STATUS_IDENTIFIED = 0;
    private static final int RISK_STATUS_IN_PROGRESS = 1;
    private static final int RISK_STATUS_CLOSED = 2;
    private static final int RISK_STATUS_OCCURRED = 3;

    /**
     * 进度加权：60% 任务 + 40% 阶段
     */
    private static final double WEIGHT_TASK = 0.6d;
    private static final double WEIGHT_PHASE = 0.4d;

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;
    @Resource
    private ProjectTaskMapper projectTaskMapper;
    @Resource
    private ProjectRiskMapper projectRiskMapper;
    @Resource
    private ProjectTeamMemberMapper projectTeamMemberMapper;

    @Override
    public ProjectPanoramicRespVO getProjectPanoramic(Long projectId) {
        // 1. 校验项目存在并装配基本信息
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectPanoramicRespVO respVO = new ProjectPanoramicRespVO();
        respVO.setId(project.getId());
        respVO.setCode(project.getCode());
        respVO.setName(project.getName());
        respVO.setCategory(project.getCategory());
        respVO.setProjectType(project.getProjectType());
        respVO.setMajorProjectFlag(project.getMajorProjectFlag());
        respVO.setManagerUserId(project.getManagerUserId());
        respVO.setStatus(project.getStatus());
        respVO.setCreateTime(project.getCreateTime());

        // 2. 客户信息（客户编号冗余存储在项目上，客户可能为空，做容错）
        respVO.setCustomerId(project.getCustomerId());
        if (project.getCustomerId() != null) {
            CustomerDO customer = customerMapper.selectById(project.getCustomerId());
            if (customer != null) {
                respVO.setCustomerCode(customer.getCode());
                respVO.setCustomerName(customer.getName());
            }
        }

        // 3. 阶段汇总（一次查询后在内存按状态分组，避免多次 count 查询）
        List<ProjectPhaseDO> phases = projectPhaseMapper.selectListByProjectId(projectId);
        respVO.setPhaseTotalCount(phases.size());
        respVO.setPhaseNotStartedCount((int) phases.stream()
                .filter(p -> Objects.equals(p.getStatus(), PHASE_STATUS_NOT_STARTED)).count());
        respVO.setPhaseInProgressCount((int) phases.stream()
                .filter(p -> Objects.equals(p.getStatus(), PHASE_STATUS_IN_PROGRESS)).count());
        respVO.setPhaseCompletedCount((int) phases.stream()
                .filter(p -> Objects.equals(p.getStatus(), PHASE_STATUS_COMPLETED)).count());
        respVO.setPhaseSkippedCount((int) phases.stream()
                .filter(p -> Objects.equals(p.getStatus(), PHASE_STATUS_SKIPPED)).count());

        // 4. 任务汇总
        List<ProjectTaskDO> tasks = projectTaskMapper.selectListByProjectId(projectId);
        respVO.setTaskTotalCount(tasks.size());
        respVO.setTaskCompletedCount((int) tasks.stream()
                .filter(t -> Objects.equals(t.getStatus(), TASK_STATUS_COMPLETED)).count());
        respVO.setTaskInProgressCount((int) tasks.stream()
                .filter(t -> Objects.equals(t.getStatus(), TASK_STATUS_IN_PROGRESS)).count());
        respVO.setTaskBlockedCount((int) tasks.stream()
                .filter(t -> Objects.equals(t.getStatus(), TASK_STATUS_BLOCKED)).count());

        // 5. 风险汇总（按状态与等级双维度统计）
        List<ProjectRiskDO> risks = projectRiskMapper.selectListByProjectId(projectId);
        respVO.setRiskTotalCount(risks.size());
        respVO.setRiskHighCount((int) risks.stream().filter(r -> "HIGH".equalsIgnoreCase(r.getRiskLevel())).count());
        respVO.setRiskMediumCount((int) risks.stream().filter(r -> "MEDIUM".equalsIgnoreCase(r.getRiskLevel())).count());
        respVO.setRiskLowCount((int) risks.stream().filter(r -> "LOW".equalsIgnoreCase(r.getRiskLevel())).count());
        respVO.setRiskIdentifiedCount((int) risks.stream()
                .filter(r -> Objects.equals(r.getStatus(), RISK_STATUS_IDENTIFIED)).count());
        respVO.setRiskInProgressCount((int) risks.stream()
                .filter(r -> Objects.equals(r.getStatus(), RISK_STATUS_IN_PROGRESS)).count());
        respVO.setRiskClosedCount((int) risks.stream()
                .filter(r -> Objects.equals(r.getStatus(), RISK_STATUS_CLOSED)).count());
        respVO.setRiskOccurredCount((int) risks.stream()
                .filter(r -> Objects.equals(r.getStatus(), RISK_STATUS_OCCURRED)).count());

        // 6. 团队成员列表
        List<ProjectTeamMemberDO> members = projectTeamMemberMapper.selectListByProjectId(projectId);
        respVO.setTeamMembers(BeanUtils.toBean(members, ProjectTeamMemberRespVO.class));

        return respVO;
    }

    @Override
    public ProjectProgressRespVO getProjectProgress(Long projectId) {
        // 1. 校验项目存在
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }

        // 2. 阶段进度 = 已完成阶段数 / 阶段总数 * 100（阶段总数为 0 时记 0）
        List<ProjectPhaseDO> phases = projectPhaseMapper.selectListByProjectId(projectId);
        int phaseTotal = phases.size();
        long phaseCompleted = phases.stream()
                .filter(p -> Objects.equals(p.getStatus(), PHASE_STATUS_COMPLETED)).count();
        int phaseProgress = phaseTotal == 0 ? 0 : (int) Math.round(phaseCompleted * 100.0d / phaseTotal);

        // 3. 任务进度 = 已完成任务数 / 任务总数 * 100（任务总数为 0 时记 0）
        List<ProjectTaskDO> tasks = projectTaskMapper.selectListByProjectId(projectId);
        int taskTotal = tasks.size();
        long taskCompleted = tasks.stream()
                .filter(t -> Objects.equals(t.getStatus(), TASK_STATUS_COMPLETED)).count();
        int taskProgress = taskTotal == 0 ? 0 : (int) Math.round(taskCompleted * 100.0d / taskTotal);

        // 4. 总体进度 = 60% 任务 + 40% 阶段，四舍五入取整
        int overallProgress = (int) Math.round(taskProgress * WEIGHT_TASK + phaseProgress * WEIGHT_PHASE);

        // 5. 装配返回
        ProjectProgressRespVO respVO = new ProjectProgressRespVO();
        respVO.setProjectId(projectId);
        respVO.setPhaseProgress(phaseProgress);
        respVO.setTaskProgress(taskProgress);
        respVO.setOverallProgress(overallProgress);
        respVO.setPhaseTotalCount(phaseTotal);
        respVO.setPhaseCompletedCount((int) phaseCompleted);
        respVO.setTaskTotalCount(taskTotal);
        respVO.setTaskCompletedCount((int) taskCompleted);
        return respVO;
    }
}
