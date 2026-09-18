package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.Subscription;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 证据资格与数量判断；不选择第一项、不调用Writer、不把事件时间当作形成边界。 */
public final class ResultEvidencePolicy {
    private ResultEvidencePolicy() { }
    public enum Eligibility { ELIGIBLE, INELIGIBLE, UNAVAILABLE }
    public enum Status { COLLECTING, SATISFIED, WAITING, AMBIGUOUS, UNAVAILABLE }
    public record Candidate(String objectId, String resultId, Long formationSequence, Observation observation) {
        public Candidate {
            if (objectId == null || objectId.isBlank() || resultId == null || resultId.isBlank())
                throw new IllegalArgumentException("EVIDENCE_IDENTITY_REQUIRED");
            Objects.requireNonNull(observation, "result observation");
        }
    }
    public record Qualification(Eligibility eligibility, String reason, Candidate candidate) { }
    public record Accumulator(long examined, long eligible, Set<String> coveredObjects, boolean unavailable) {
        public Accumulator {
            if (examined < 0 || eligible < 0 || eligible > examined || coveredObjects == null || coveredObjects.size() > eligible)
                throw new IllegalArgumentException("EVIDENCE_ACCUMULATOR_INVALID");
            coveredObjects = Set.copyOf(coveredObjects);
        }
        public static Accumulator empty() { return new Accumulator(0, 0, Set.of(), false); }
    }
    public record Decision(Status status, long examined, long eligible, Set<String> missingObjects) {
        public Decision { missingObjects = Set.copyOf(missingObjects); }
        public boolean satisfied() { return status == Status.SATISFIED; }
    }

    public static Qualification qualify(Subscription subscription, long baseline, long through, Candidate candidate) {
        Objects.requireNonNull(subscription, "subscription"); Objects.requireNonNull(candidate, "candidate");
        if (baseline < 0 || through < baseline || candidate.formationSequence() != null
                && (candidate.formationSequence() <= 0 || candidate.formationSequence() > through))
            throw new IllegalArgumentException("EVIDENCE_FORMATION_BOUNDARY_INVALID");
        var observation = candidate.observation();
        if (observation.status() == cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Status.UNAVAILABLE)
            return new Qualification(Eligibility.UNAVAILABLE, observation.reason(), candidate);
        if (observation.result() == null) return reject(candidate, observation.reason());
        var result = observation.result();
        if (!ResultSubscriptionContract.type(subscription).equals(result.type()))
            throw new IllegalArgumentException("EVIDENCE_RESULT_TYPE_MISMATCH");
        if (!candidate.objectId().equals(result.objectId()) || !candidate.resultId().equals(result.resultId()))
            return reject(candidate, "RESULT_IDENTITY_REPLACED");
        if ("OBJECTS".equals(subscription.scope().mode()) && !subscription.scope().objectIds().contains(result.objectId()))
            return reject(candidate, "RESULT_OUTSIDE_FROZEN_SCOPE");
        if (result.validity() == Validity.REVOKED) return reject(candidate, "RESULT_REVOKED");
        if ("CURRENT_VALID".equals(subscription.policy().validity()) && result.validity() != Validity.CURRENT)
            return reject(candidate, "RESULT_NOT_CURRENT");
        switch (subscription.policy().acquisition()) {
            case "NEW_RESULT" -> {
                if (candidate.formationSequence() == null) return reject(candidate, "RESULT_FORMATION_UNPROVEN");
                if (candidate.formationSequence() <= baseline) return reject(candidate, "RESULT_FORMED_BEFORE_ROUND");
            }
            case "PINNED_RESULT" -> {
                if (!Objects.equals(subscription.policy().pinnedResultId(), result.resultId())) return reject(candidate, "RESULT_NOT_PINNED");
            }
            case "REUSE_EXISTING" -> { }
            default -> throw new IllegalArgumentException("EVIDENCE_ACQUISITION_UNSUPPORTED");
        }
        return new Qualification(Eligibility.ELIGIBLE, "RESULT_QUALIFIED", candidate);
    }

    /** 跨页重复由持久层的扫描/候选唯一键与CAS阻止；本页还要拒绝重复原生结果身份。 */
    public static Accumulator accumulate(Subscription subscription, Accumulator previous, List<Qualification> page) {
        long examined = previous.examined(); long eligible = previous.eligible();
        var covered = new HashSet<>(previous.coveredObjects()); var identities = new HashSet<List<String>>();
        boolean unavailable = previous.unavailable();
        for (var item : page) {
            if (!identities.add(List.of(item.candidate().objectId(), item.candidate().resultId())))
                throw new IllegalArgumentException("EVIDENCE_DUPLICATE_RESULT");
            examined = Math.addExact(examined, 1);
            unavailable |= item.eligibility() == Eligibility.UNAVAILABLE;
            if (item.eligibility() == Eligibility.ELIGIBLE) {
                eligible = Math.addExact(eligible, 1);
                if ("ALL_EXPECTED".equals(subscription.policy().selection())) covered.add(item.candidate().objectId());
            }
        }
        return new Accumulator(examined, eligible, covered, unavailable);
    }

    public static Decision decide(Subscription subscription, Accumulator accumulated, boolean complete) {
        var missing = new HashSet<String>();
        if ("ALL_EXPECTED".equals(subscription.policy().selection())) {
            if (!"OBJECTS".equals(subscription.scope().mode()) || subscription.scope().objectIds().isEmpty())
                throw new IllegalArgumentException("EVIDENCE_EXPECTED_OBJECTS_REQUIRED");
            missing.addAll(subscription.scope().objectIds()); missing.removeAll(accumulated.coveredObjects());
        }
        Status status;
        if (!complete) status = Status.COLLECTING;
        else if (accumulated.unavailable()) status = Status.UNAVAILABLE;
        else status = switch (subscription.policy().selection()) {
            case "EXACT_ONE" -> accumulated.eligible() == 1 ? Status.SATISFIED : accumulated.eligible() == 0 ? Status.WAITING : Status.AMBIGUOUS;
            case "ANY_MATCHING" -> accumulated.eligible() > 0 ? Status.SATISFIED : Status.WAITING;
            case "ALL_EXPECTED" -> missing.isEmpty() ? Status.SATISFIED : Status.WAITING;
            default -> throw new IllegalArgumentException("EVIDENCE_SELECTION_UNSUPPORTED");
        };
        return new Decision(status, accumulated.examined(), accumulated.eligible(), missing);
    }
    private static Qualification reject(Candidate candidate, String reason) {
        return new Qualification(Eligibility.INELIGIBLE, reason, candidate);
    }
}
