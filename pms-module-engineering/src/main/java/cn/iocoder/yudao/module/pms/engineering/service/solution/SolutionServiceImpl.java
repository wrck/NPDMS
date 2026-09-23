package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionGenerateDraftReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 实施方案 Service 实现（FR-ENG-011 / FR-ENG-013）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 审批通过时冻结基线版本号并记录审核人与审核时间。
 */
@Service
@Validated
public class SolutionServiceImpl implements SolutionService {

    /** 交付件来源类型：批准实施方案（4.1→6.4 自动归集）。 */
    public static final String SOURCE_TYPE_SOLUTION = "SOLUTION";

    @Resource
    private SolutionMapper solutionMapper;
    @Resource
    private DeliverableMapper deliverableMapper;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;

    @Resource
    private cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper tieredReviewMapper;

    @Resource
    private cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewPolicyService reviewPolicies;

    @Resource
    private cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents completionEvents;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSolution(SolutionSaveReqVO createReqVO) {
        SolutionDO solution = BeanUtils.toBean(createReqVO, SolutionDO.class);
        solution.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.SOLUTION, solutionMapper,
                SolutionDO::getProjectId, SolutionDO::getCode));
        solution.setStatus(0);
        solution.setVersion(0);
        solution.setBaselineVersion(null);
        solution.setApprovedBy(null);
        solution.setApprovedTime(null);
        solution.setApprovalOpinion(null);
        if (solution.getReviewLevel() == null) {
            solution.setReviewLevel(0);
        }
        solutionMapper.insert(solution);
        completionChanged(solution);
        return solution.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSolution(SolutionSaveReqVO updateReqVO) {
        SolutionDO existing = validateSolutionExists(updateReqVO.getId());
        validateStatus(existing, 0);
        validateVersion(existing, updateReqVO.getVersion());
        SolutionDO update = BeanUtils.toBean(updateReqVO, SolutionDO.class);
        update.setStatus(existing.getStatus());
        update.setVersion(existing.getVersion());
        update.setBaselineVersion(existing.getBaselineVersion());
        update.setApprovedBy(existing.getApprovedBy());
        update.setApprovedTime(existing.getApprovedTime());
        update.setApprovalOpinion(existing.getApprovalOpinion());
        updateRecord(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSolution(Long id) {
        SolutionDO existing = validateSolutionExists(id);
        validateStatus(existing, 0);
        solutionMapper.deleteById(id);
        completionChanged(existing);
    }

    @Override
    public SolutionDO getSolution(Long id) {
        return solutionMapper.selectById(id);
    }

    @Override
    public PageResult<SolutionDO> getSolutionPage(SolutionPageReqVO pageReqVO) {
        return solutionMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSolution(Long id) {
        SolutionDO solution = validateSolutionExists(id);
        validateStatus(solution, 0); // 草稿 → 已提交
        if ("IMPLEMENTATION".equals(solution.getSolutionType())) reviewPolicies.freeze(solution, 0);
        updateStatus(solution, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startReview(Long id) {
        SolutionDO solution = validateSolutionExists(id);
        validateStatus(solution, 1); // 已提交 → 审批中
        updateStatus(solution, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveSolution(SolutionApproveReqVO reqVO) {
        SolutionDO solution = validateSolutionExists(reqVO.getId());
        validateVersion(solution, reqVO.getVersion());
        validateStatus(solution, 2); // 审批中 → 已通过
        if ("IMPLEMENTATION".equals(solution.getSolutionType())) reviewPolicies.requireOrdinaryApproval(solution);
        if (!Objects.equals(solution.getReviewLevel(), 0)) {
            throw exception(SOLUTION_REVIEW_NOT_CONNECTED);
        }
        solution.setStatus(3);
        solution.setApprovalOpinion(reqVO.getApprovalOpinion());
        Long approverId = SecurityFrameworkUtils.getLoginUserId();
        solution.setApprovedBy(approverId);
        solution.setApprovedTime(LocalDateTime.now());
        solution.setBaselineVersion(solution.getVersion() + 1); // 冻结基线版本
        updateRecord(solution);
        archiveApprovedSolution(solution, approverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectSolution(SolutionApproveReqVO reqVO) {
        SolutionDO solution = validateSolutionExists(reqVO.getId());
        validateVersion(solution, reqVO.getVersion());
        validateStatus(solution, 2); // 审批中 → 已驳回
        solution.setStatus(4);
        solution.setApprovalOpinion(reqVO.getApprovalOpinion());
        solution.setApprovedBy(SecurityFrameworkUtils.getLoginUserId());
        solution.setApprovedTime(LocalDateTime.now());
        updateRecord(solution);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawSolution(Long id) {
        SolutionDO solution = validateSolutionExists(id);
        validateStatus(solution, 2); // 审批中 → 已撤回
        updateStatus(solution, 5);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateSolution(Long id) {
        SolutionDO solution = validateSolutionExists(id);
        validateStatus(solution, 2); // 审批中 → 已终止
        updateStatus(solution, 6);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateDraft(SolutionGenerateDraftReqVO reqVO) {
        // 生成方案草稿，编码由系统按项目编码自动生成
        SolutionDO solution = new SolutionDO();
        solution.setProjectId(reqVO.getProjectId());
        solution.setCode(recordCodeGenerator.next(reqVO.getProjectId(),
                EngineeringRecordCodeGenerator.SOLUTION, solutionMapper,
                SolutionDO::getProjectId, SolutionDO::getCode));
        solution.setName(reqVO.getSolutionName() != null ? reqVO.getSolutionName() : solution.getCode());
        solution.setSolutionType("IMPLEMENTATION");
        solution.setReviewLevel(0);
        solution.setStatus(0); // 草稿
        solution.setVersion(0);
        solutionMapper.insert(solution);
        completionChanged(solution);
        return solution.getId();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 批准方案同步归集交付件（4.1→6.4，ACC-04）：同一方案幂等，不覆盖既有归档记录。
     * 方案基线一经批准即冻结，归档件直接进入已归集状态；失败随当前事务回滚。
     */
    private void archiveApprovedSolution(SolutionDO solution, Long approverId) {
        if (deliverableMapper.selectByProjectAndSource(solution.getProjectId(), SOURCE_TYPE_SOLUTION, solution.getId()) != null) {
            return;
        }
        DeliverableDO deliverable = new DeliverableDO();
        deliverable.setProjectId(solution.getProjectId());
        deliverable.setCode(recordCodeGenerator.next(solution.getProjectId(),
                EngineeringRecordCodeGenerator.DELIVERABLE, deliverableMapper,
                DeliverableDO::getProjectId, DeliverableDO::getCode));
        deliverable.setName(StringUtils.defaultIfBlank(solution.getName(), solution.getCode())
                + "（基线v" + solution.getBaselineVersion() + "）");
        deliverable.setDeliverableType("IMPLEMENTATION");
        deliverable.setSourceType(SOURCE_TYPE_SOLUTION);
        deliverable.setSourceId(solution.getId());
        deliverable.setStatus(EngStatusEnum.DELIVERABLE_ARCHIVED);
        deliverable.setArchivedBy(approverId);
        deliverable.setArchivedTime(LocalDateTime.now());
        deliverable.setRemark("实施方案审批通过自动归档");
        deliverable.setVersion(0);
        deliverableMapper.insert(deliverable);
    }

    private SolutionDO validateSolutionExists(Long id) {
        SolutionDO solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw exception(SOLUTION_NOT_EXISTS);
        }
        if ("IMPLEMENTATION".equals(solution.getSolutionType())) {
            var query = new cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper.SolutionReviewQuery(
                    solution.getTenantId(), solution.getProjectId(), solution.getId(), true);
            var locked = tieredReviewMapper.source(query);
            if (locked == null || !Objects.equals(locked.getVersion(), solution.getVersion())) throw exception(SOLUTION_VERSION_NOT_MATCH);
            var review = tieredReviewMapper.bySolution(query);
            if (review != null && "RUNNING".equals(review.getStatus()))
                throw new IllegalStateException("此方案正在分级审批，请在关联的 BPM 流程中处理");
            solution = locked;
        }
        return solution;
    }

    private void validateVersion(SolutionDO solution, Integer version) {
        if (version != null && !Objects.equals(solution.getVersion(), version)) {
            throw exception(SOLUTION_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(SolutionDO solution, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(solution.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(SOLUTION_STATUS_INVALID);
    }

    private void updateStatus(SolutionDO solution, int newStatus) {
        solution.setStatus(newStatus);
        updateRecord(solution);
    }

    private void updateRecord(SolutionDO solution) {
        if (solutionMapper.updateById(solution) != 1) {
            throw exception(SOLUTION_VERSION_NOT_MATCH);
        }
        completionChanged(solution);
    }

    private void completionChanged(SolutionDO solution) {
        completionEvents.changed(solution.getProjectId(), "ImplementationSolution", solution.getId(),
                SecurityFrameworkUtils.getLoginUserId(), "solution:" + solution.getId() + ":" + solution.getVersion());
    }
}
