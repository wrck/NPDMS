package cn.iocoder.yudao.module.pms.platform.support.result;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSelectionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription.ResultSubscriptionPort.DecisionStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 结果选择语义（自旧 ResultEvidencePolicy 迁移，语义保持同名同义）：
 * 不选择第一项、不写入、不把事件时间/字段修改时间当作形成边界；轮次窗口以形成序号表达。
 * 状态：COLLECTING（集合未读完）/ SATISFIED / WAITING / AMBIGUOUS / UNAVAILABLE（存在不可读成员）。
 */
public final class ResultSelectionEvaluator {

    private ResultSelectionEvaluator() {
    }

    public enum Eligibility { ELIGIBLE, INELIGIBLE, UNAVAILABLE }

    public record Candidate(long objectId, String resultId, long formationSequence, boolean valid) {
    }

    public record Qualification(Eligibility eligibility, String reason, Candidate candidate) {
    }

    public record Accumulator(long examined, long eligible, Set<Long> coveredObjects, boolean unavailable) {

        public Accumulator {
            coveredObjects = Set.copyOf(coveredObjects);
        }

        public static Accumulator empty() {
            return new Accumulator(0, 0, Set.of(), false);
        }
    }

    public record Decision(DecisionStatus status, long examined, long eligible,
                           Set<Long> missingObjects, List<String> adoptedResultIds) {

        public boolean satisfied() {
            return status == DecisionStatus.SATISFIED;
        }
    }

    public static Qualification qualify(ResultSelectionPolicy policy, long baseline, long through,
                                        Candidate candidate) {
        if (baseline < 0 || through < baseline
                || candidate.formationSequence() <= 0 || candidate.formationSequence() > through) {
            throw new IllegalArgumentException("EVIDENCE_FORMATION_BOUNDARY_INVALID");
        }
        // 有效结果由结果层"仅返回有效结果"的读取保证进入候选；撤销结果显式拒绝。
        if (!candidate.valid()) {
            return new Qualification(Eligibility.INELIGIBLE, "RESULT_REVOKED", candidate);
        }
        switch (policy.acquisition()) {
            case NEW_RESULT -> {
                if (candidate.formationSequence() <= baseline) {
                    return new Qualification(Eligibility.INELIGIBLE, "RESULT_FORMED_BEFORE_ROUND", candidate);
                }
            }
            case PINNED_RESULT -> {
                // 固定结果查不到时不回退最新：候选结果身份与固定身份不一致即拒绝。
                if (!Objects.equals(policy.pinnedResultId(), candidate.resultId())) {
                    return new Qualification(Eligibility.INELIGIBLE, "RESULT_NOT_PINNED", candidate);
                }
            }
            case REUSE_EXISTING -> {
            }
        }
        return new Qualification(Eligibility.ELIGIBLE, "RESULT_QUALIFIED", candidate);
    }

    public static Accumulator accumulate(ResultSelectionPolicy policy, Accumulator previous,
                                         List<Qualification> page) {
        long examined = previous.examined();
        long eligible = previous.eligible();
        var covered = new HashSet<>(previous.coveredObjects());
        var identities = new HashSet<List<String>>();
        boolean unavailable = previous.unavailable();
        for (Qualification item : page) {
            // 同页拒绝重复原生结果身份；跨页重复由持久层扫描唯一性阻止。
            if (!identities.add(List.of(String.valueOf(item.candidate().objectId()),
                    item.candidate().resultId()))) {
                throw new IllegalArgumentException("EVIDENCE_DUPLICATE_RESULT");
            }
            examined = Math.addExact(examined, 1);
            unavailable |= item.eligibility() == Eligibility.UNAVAILABLE;
            if (item.eligibility() == Eligibility.ELIGIBLE) {
                eligible = Math.addExact(eligible, 1);
                if (policy.selection() == ResultSelectionPolicy.Selection.ALL_EXPECTED) {
                    covered.add(item.candidate().objectId());
                }
            }
        }
        return new Accumulator(examined, eligible, covered, unavailable);
    }

    public static Decision decide(ResultSelectionPolicy policy, Accumulator accumulated, boolean complete) {
        var missing = new HashSet<Long>();
        if (policy.selection() == ResultSelectionPolicy.Selection.ALL_EXPECTED) {
            if (policy.expectedObjectIds().isEmpty()) {
                throw new IllegalArgumentException("EVIDENCE_EXPECTED_OBJECTS_REQUIRED");
            }
            missing.addAll(policy.expectedObjectIds());
            missing.removeAll(accumulated.coveredObjects());
        }
        DecisionStatus status;
        if (!complete) {
            status = DecisionStatus.COLLECTING;
        } else if (accumulated.unavailable()) {
            status = DecisionStatus.UNAVAILABLE;
        } else {
            status = switch (policy.selection()) {
                case EXACT_ONE -> accumulated.eligible() == 1 ? DecisionStatus.SATISFIED
                        : accumulated.eligible() == 0 ? DecisionStatus.WAITING
                        : DecisionStatus.AMBIGUOUS;
                case ANY_MATCHING -> accumulated.eligible() > 0 ? DecisionStatus.SATISFIED
                        : DecisionStatus.WAITING;
                case ALL_EXPECTED -> missing.isEmpty() ? DecisionStatus.SATISFIED
                        : DecisionStatus.WAITING;
            };
        }
        return new Decision(status, accumulated.examined(), accumulated.eligible(), missing, List.of());
    }
}
