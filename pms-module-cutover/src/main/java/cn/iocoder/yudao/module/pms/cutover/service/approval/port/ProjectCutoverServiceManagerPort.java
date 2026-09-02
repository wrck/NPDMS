package cn.iocoder.yudao.module.pms.cutover.service.approval.port;

import java.time.LocalDateTime;
import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.SERVICE_MANAGER_ROLES;
import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.require;

public interface ProjectCutoverServiceManagerPort {

    ServiceManagerFact inspectCurrent(long tenantId, long projectId, LocalDateTime checkedAt);

    ServiceManagerRevalidation lockAndRevalidate(ServiceManagerFact expected);

    record ServiceManagerFact(Outcome outcome, long tenantId, long projectId, Long userId,
                              String roleCode, Integer projectVersion,
                              Long participantFactVersion, LocalDateTime checkedAt) {
        public ServiceManagerFact {
            require(outcome != null && tenantId > 0 && projectId > 0 && checkedAt != null,
                    "serviceManagerFact");
            if (outcome == Outcome.FOUND) {
                require(userId != null && userId > 0 && SERVICE_MANAGER_ROLES.contains(roleCode),
                        "serviceManager");
                require(projectVersion != null && projectVersion >= 0
                                && participantFactVersion != null && participantFactVersion >= 0,
                        "serviceManagerVersion");
            } else {
                require(userId == null && roleCode == null && projectVersion == null
                                && participantFactVersion == null, "notUniqueFact");
            }
        }
    }

    record ServiceManagerRevalidation(Revalidation outcome, ServiceManagerFact current) {
        public ServiceManagerRevalidation {
            require(outcome != null && current != null, "serviceManagerRevalidation");
            require(outcome != Revalidation.VALID || current.outcome() == Outcome.FOUND,
                    "validServiceManager");
        }
    }

    enum Outcome { FOUND, NOT_UNIQUE }
    enum Revalidation { VALID, STALE }
}
