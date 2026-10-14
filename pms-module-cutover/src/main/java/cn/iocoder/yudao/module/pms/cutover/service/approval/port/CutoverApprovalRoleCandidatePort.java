package cn.iocoder.yudao.module.pms.cutover.service.approval.port;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.ROLE_GROUP_CODES;
import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.require;

public interface CutoverApprovalRoleCandidatePort {

    CandidateSet inspectCandidates(RoleQuery query);

    CandidateRevalidation lockAndRevalidate(CandidateSet expected);

    RoleReference inspectRole(RoleQuery query);

    List<ActorMembership> inspectActor(ActorQuery query);

    List<ActorMembership> lockAndInspectActor(ActorQuery query);

    record RoleQuery(long tenantId, String roleGroupCode) { }
    record ActorQuery(long tenantId, long actorId) { }

    record RoleReference(long tenantId, long roleId, String roleGroupCode) {
        public RoleReference {
            require(tenantId > 0 && roleId > 0 && ROLE_GROUP_CODES.contains(roleGroupCode), "roleReference");
        }
        public String candidateGroupKey() { return "ROLE:" + roleId; }
    }

    record ActorMembership(RoleReference role, Candidate candidate) {
        public ActorMembership {
            require(role != null && candidate != null && role.roleId() == candidate.roleGroupId(), "actorMembership");
        }
    }

    record CandidateSet(long tenantId, String roleGroupCode, List<Candidate> candidates) {
        public CandidateSet {
            require(tenantId > 0 && ROLE_GROUP_CODES.contains(roleGroupCode) && candidates != null,
                    "candidateSet");
            candidates = candidates.stream().sorted(Comparator.comparingLong(Candidate::adminUserId)).toList();
            require(new HashSet<>(candidates.stream().map(Candidate::adminUserId).toList()).size()
                    == candidates.size(), "candidateUserId");
        }
    }

    record Candidate(long adminUserId, long roleGroupId, long roleMembershipVersion,
                     long userStatusVersion) {
        public Candidate {
            require(adminUserId > 0 && roleGroupId > 0 && roleMembershipVersion >= 0
                    && userStatusVersion >= 0, "candidate");
        }
    }

    record CandidateRevalidation(Revalidation outcome, CandidateSet current) {
        public CandidateRevalidation {
            require(outcome != null && current != null, "candidateRevalidation");
        }
    }

    record ExplicitCandidate(Eligibility outcome, Candidate candidate) {
        public ExplicitCandidate {
            require(outcome != null, "explicitCandidate");
            require((outcome == Eligibility.ELIGIBLE && candidate != null)
                    || (outcome == Eligibility.INELIGIBLE && candidate == null), "explicitCandidateResult");
        }
    }

    enum Revalidation { VALID, STALE }
    enum Eligibility { ELIGIBLE, INELIGIBLE }
}
