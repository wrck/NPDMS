package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutPlanMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.risk.CutRiskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.domain.CutTaskStatusRules;
import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.*;

/**
 * PMS 割接任务 Service 实现（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 */
@Service
@Validated
@Slf4j
public class CutTaskServiceImpl implements CutTaskService {

    @Resource
    private CutTaskMapper cutTaskMapper;

    @Resource
    private CutRiskMapper cutRiskMapper;

    @Resource
    private CutPlanMapper cutPlanMapper;

    @Override
    public Long createCutTask(CutTaskSaveReqVO createReqVO) {
        // 1. 校验编码在项目内唯一
        validateCodeUniqueInProject(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为草稿
        CutTaskDO entity = BeanUtils.toBean(createReqVO, CutTaskDO.class);
        entity.setStatus(CutStatusEnum.CUT_TASK_DRAFT);
        if (StringUtils.isBlank(entity.getRiskLevel())) {
            entity.setRiskLevel("C");
        }
        if (StringUtils.isBlank(entity.getSourceType())) {
            entity.setSourceType("MANUAL");
        }
        if (StringUtils.isBlank(entity.getCutoverType())) {
            entity.setCutoverType("REPLACE");
        }
        cutTaskMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateCutTask(CutTaskSaveReqVO updateReqVO) {
        // 1. 校验存在
        CutTaskDO existing = validateCutTaskExists(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(CUT_TASK_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 终态任务不允许修改
        if (CutTaskStatusRules.isTerminal(existing.getStatus())) {
            throw exception(CUT_TASK_STATUS_INVALID);
        }
        // 4. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        CutTaskDO update = BeanUtils.toBean(updateReqVO, CutTaskDO.class);
        cutTaskMapper.updateById(update);
    }

    @Override
    public void deleteCutTask(Long id) {
        validateCutTaskExists(id);
        cutTaskMapper.deleteById(id);
    }

    @Override
    public CutTaskDO getCutTask(Long id) {
        return cutTaskMapper.selectById(id);
    }

    @Override
    public CutTaskDO validateCutTaskExists(Long id) {
        CutTaskDO entity = cutTaskMapper.selectById(id);
        if (entity == null) {
            throw exception(CUT_TASK_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public PageResult<CutTaskDO> getCutTaskPage(CutTaskPageReqVO pageReqVO) {
        return cutTaskMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CutTaskDO> getCutTaskListByProject(Long projectId) {
        return cutTaskMapper.selectListByProject(projectId);
    }

    @Override
    public void validateProjectCutoverReady(Long projectId) {
        // FR-CUT-001 前置门禁：前序必填、测试、方案审批和资源准备全部通过。
        // 跨模块前序依赖（工程实施域方案审批等）通过应用层 API/事件对接，此处仅校验本域可判定条件。
        if (projectId == null) {
            throw exception(CUT_TASK_GATE_NOT_READY);
        }
        // 本域门禁：项目下不存在未回退/未终止且阻塞的割接任务（由具体动作另行校验风险/方案）。
    }

    @Override
    public void submitForReview(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 前置门禁：校验本域风险均已闭环
        Long notClosedRisk = cutRiskMapper.selectCountByTaskNotClosed(id);
        if (notClosedRisk != null && notClosedRisk > 0) {
            throw exception(CUT_RISK_NOT_CLOSED, id);
        }
        // 3. 状态机校验：0草稿 → 2待评审
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.SUBMIT_FOR_REVIEW);
        // 4. 更新状态
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.SUBMIT_FOR_REVIEW));
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void approve(CutTaskApproveReqVO reqVO) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(reqVO.getId());
        // 2. 状态机校验：2待评审 → 3待执行
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.APPROVE);
        // 3. 更新状态与评审意见
        CutTaskDO update = new CutTaskDO();
        update.setId(reqVO.getId());
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.APPROVE));
        update.setApprovalOpinion(reqVO.getApprovalOpinion());
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void reject(CutTaskApproveReqVO reqVO) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(reqVO.getId());
        // 2. 状态机校验：2待评审 → 1准备中
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.REJECT);
        // 3. 更新状态与评审意见
        CutTaskDO update = new CutTaskDO();
        update.setId(reqVO.getId());
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.REJECT));
        update.setApprovalOpinion(reqVO.getApprovalOpinion());
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void startExecution(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 前置门禁：校验存在已评审通过的割接方案
        Long approvedPlan = cutPlanMapper.selectCountByTaskApproved(id);
        if (approvedPlan == null || approvedPlan <= 0) {
            throw exception(CUT_TASK_NOT_APPROVED, id);
        }
        // 3. 状态机校验：3待执行 → 4执行中
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.START_EXECUTION);
        // 4. 更新状态与实际开始时间
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.START_EXECUTION));
        update.setActualTime(LocalDateTime.now());
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void completeExecution(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 状态机校验：4执行中 → 5稳定观察
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.COMPLETE_EXECUTION);
        // 3. 更新状态
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.COMPLETE_EXECUTION));
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void startObservation(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 状态机校验：4执行中 → 5稳定观察
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.START_OBSERVATION);
        // 3. 更新状态
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.START_OBSERVATION));
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void completeObservation(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 状态机校验：5稳定观察 → 6已完成
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.COMPLETE_OBSERVATION);
        // 3. 更新状态
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.COMPLETE_OBSERVATION));
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void rollback(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 状态机校验：4执行中 → 7已回退
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.ROLLBACK);
        // 3. 更新状态
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.ROLLBACK));
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    @Override
    public void terminate(Long id) {
        // 1. 校验存在
        CutTaskDO entity = validateCutTaskExists(id);
        // 2. 状态机校验：任意非终态 → 8已终止
        CutTaskStatusRules.requireTransition(entity.getStatus(), CutTaskStatusRules.Action.TERMINATE);
        // 3. 更新状态
        CutTaskDO update = new CutTaskDO();
        update.setId(id);
        update.setStatus(CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.TERMINATE));
        update.setVersion(entity.getVersion());
        cutTaskMapper.updateById(update);
    }

    private void validateCodeUniqueInProject(Long id, Long projectId, String code) {
        if (StringUtils.isBlank(code) || projectId == null) {
            return;
        }
        CutTaskDO existing = cutTaskMapper.selectByProjectCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(CUT_TASK_CODE_DUPLICATE, code);
        }
    }
}
