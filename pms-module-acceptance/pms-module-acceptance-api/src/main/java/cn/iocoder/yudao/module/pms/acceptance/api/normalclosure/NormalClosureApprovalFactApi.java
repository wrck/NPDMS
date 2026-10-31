package cn.iocoder.yudao.module.pms.acceptance.api.normalclosure;

/** ACC-owned approval evidence. Caller must hold root/project locks in the same transaction. */
public interface NormalClosureApprovalFactApi {
    Fact requireApprovedApplicant(Query query);

    record Query(Long tenantId, Long projectId, Long applicationId, String processInstanceId,
                 Long applicantUserId, Long reviewerUserId, Long expectedProjectVersion, Long expectedTreeVersion) { }

    record Fact(Long snapshotId, Long applicationVersion, String fromStage, Long serviceManagerUserId,
                String sourceDigest) { }
}
