package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewPolicyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewPolicyMapper;
import cn.iocoder.yudao.module.pms.project.api.rule.ProjectFieldRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.SOLUTION_REVIEW_POLICY_INVALID;

/** Two explicit outcomes prevent missing source values from silently selecting ordinary review. */
@Service @RequiredArgsConstructor
public class SolutionReviewPolicyService {
    public static final String MAJOR = "SOLUTION_MAJOR_REVIEW_REQUIRED";
    public static final String ORDINARY = "SOLUTION_ORDINARY_REVIEW_ALLOWED";
    private final ProjectFieldRuleApi rules;
    private final SolutionReviewPolicyMapper records;
    public record Policy(boolean configured, Integer reviewLevel, String reason, String evidenceJson) { }

    public Policy preview(Long projectId) { return policy(evaluate(projectId, false)); }

    /** Caller holds the SOL object lock; the decision is inserted in the same submission transaction. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void freeze(SolutionDO solution, int requiredLevel) {
        var stored = stored(solution);
        if (stored != null) {
            requireLevel(stored.getReviewLevel(), requiredLevel);
            solution.setReviewLevel(stored.getReviewLevel());
            return;
        }
        var decision = policy(evaluate(solution.getProjectId(), true));
        if (!decision.configured()) return;
        if (decision.reviewLevel() == null) throw exception(SOLUTION_REVIEW_POLICY_INVALID, decision.reason());
        requireLevel(decision.reviewLevel(), requiredLevel);
        var record = new SolutionReviewPolicyDO();
        record.setProjectId(solution.getProjectId()); record.setSolutionId(solution.getId());
        record.setSourceVersion(solution.getVersion()); record.setReviewLevel(decision.reviewLevel()); record.setEvidenceJson(decision.evidenceJson());
        records.insert(record);
        solution.setReviewLevel(decision.reviewLevel());
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireOrdinaryApproval(SolutionDO solution) {
        var stored = stored(solution);
        if (stored != null) { requireLevel(stored.getReviewLevel(), 0); return; }
        if (evaluate(solution.getProjectId(), true).configured())
            throw exception(SOLUTION_REVIEW_POLICY_INVALID, "此方案缺少提交时的审核判定，请复制为新版本后重新提交");
    }

    private SolutionReviewPolicyDO stored(SolutionDO solution) {
        return records.bySolution(new SolutionReviewPolicyMapper.Selection(TenantContextHolder.getRequiredTenantId(), solution.getProjectId(), solution.getId()));
    }
    private ProjectFieldRuleApi.Query query(Long projectId) {
        return new ProjectFieldRuleApi.Query(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), projectId, List.of(MAJOR, ORDINARY));
    }
    private void requireLevel(Integer actual, int expected) {
        if (!Objects.equals(actual, expected)) throw exception(SOLUTION_REVIEW_POLICY_INVALID, expected == 0
                ? "项目规则要求工程管理部复审，请使用分级审核入口" : "项目规则适用普通审核，请使用普通审核入口");
    }
    private Policy policy(ProjectFieldRuleApi.Evaluation result) {
        if (result == null) throw exception(SOLUTION_REVIEW_POLICY_INVALID, "审核适用规则不可用");
        if (!result.configured()) return new Policy(false, null, null, null);
        String major = result.outcomes().get(MAJOR), ordinary = result.outcomes().get(ORDINARY);
        Integer level = null;
        if ("MATCHED".equals(major) && "NOT_MATCHED".equals(ordinary)) level = 1;
        else if ("NOT_MATCHED".equals(major) && "MATCHED".equals(ordinary)) level = 0;
        return new Policy(true, level, level == null ? "项目审核判定依据缺失或规则冲突，请由工程管理部补齐来源及规则" : null, result.evidenceJson());
    }
    private ProjectFieldRuleApi.Evaluation evaluate(Long projectId, boolean lock) {
        try { return lock ? rules.lockAndEvaluate(query(projectId)) : rules.evaluate(query(projectId)); }
        catch (IllegalArgumentException invalid) { throw exception(SOLUTION_REVIEW_POLICY_INVALID, invalid.getMessage()); }
    }
}
