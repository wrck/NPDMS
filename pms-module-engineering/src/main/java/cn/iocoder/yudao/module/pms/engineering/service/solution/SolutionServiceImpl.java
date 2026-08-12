package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionGenerateDraftReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import jakarta.annotation.Resource;
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

    @Resource
    private SolutionMapper solutionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSolution(SolutionSaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        SolutionDO solution = BeanUtils.toBean(createReqVO, SolutionDO.class);
        if (solution.getStatus() == null) {
            solution.setStatus(0); // 草稿
        }
        if (solution.getVersion() == null) {
            solution.setVersion(0);
        }
        if (solution.getReviewLevel() == null) {
            solution.setReviewLevel(0);
        }
        solutionMapper.insert(solution);
        return solution.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSolution(SolutionSaveReqVO updateReqVO) {
        SolutionDO existing = validateSolutionExists(updateReqVO.getId());
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        SolutionDO update = BeanUtils.toBean(updateReqVO, SolutionDO.class);
        solutionMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSolution(Long id) {
        validateSolutionExists(id);
        solutionMapper.deleteById(id);
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
        solution.setStatus(3);
        solution.setApprovalOpinion(reqVO.getApprovalOpinion());
        solution.setApprovedBy(SecurityFrameworkUtils.getLoginUserId());
        solution.setApprovedTime(LocalDateTime.now());
        solution.setBaselineVersion(solution.getVersion() + 1); // 冻结基线版本
        solution.setVersion(solution.getVersion() + 1);
        solutionMapper.updateById(solution);
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
        solution.setVersion(solution.getVersion() + 1);
        solutionMapper.updateById(solution);
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
        // 1. 校验项目内方案编码唯一
        validateCodeUnique(reqVO.getProjectId(), reqVO.getSolutionCode(), null);
        // 2. 生成方案草稿
        SolutionDO solution = new SolutionDO();
        solution.setProjectId(reqVO.getProjectId());
        solution.setCode(reqVO.getSolutionCode());
        solution.setName(reqVO.getSolutionName() != null ? reqVO.getSolutionName() : reqVO.getSolutionCode());
        solution.setSolutionType("IMPLEMENTATION");
        solution.setReviewLevel(0);
        solution.setStatus(0); // 草稿
        solution.setVersion(0);
        solutionMapper.insert(solution);
        return solution.getId();
    }

    // ==================== 内部工具方法 ====================

    private SolutionDO validateSolutionExists(Long id) {
        SolutionDO solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw exception(SOLUTION_NOT_EXISTS);
        }
        return solution;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        SolutionDO existing = solutionMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(SOLUTION_CODE_DUPLICATE);
        }
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
        solution.setVersion(solution.getVersion() + 1);
        solutionMapper.updateById(solution);
    }
}
