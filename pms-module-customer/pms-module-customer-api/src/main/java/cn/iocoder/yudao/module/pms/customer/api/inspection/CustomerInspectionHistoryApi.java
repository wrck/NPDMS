package cn.iocoder.yudao.module.pms.customer.api.inspection;

import lombok.Builder;
import java.util.List;

/** SRV归档事务内登记来源引用；无外部写入口，不复制来源业务真值。 */
public interface CustomerInspectionHistoryApi {
    ArchiveReference register(ArchiveCommand command);

    enum Kind { DEVICE, EXECUTION, RESULT, REPORT, ANNOTATION, ISSUE, REMEDIATION, EVIDENCE }
    record SourceReference(Kind kind, Long sourceId) { }
    @Builder
    record ArchiveCommand(Long tenantId, Long actorId, Long customerId, String customerCode,
                          Long projectId, Long taskId, Long archivedTaskVersion, String taskCode,
                          String taskName, Long reportId, List<SourceReference> references) { }
    record ArchiveReference(Long id, Long taskId, Long taskVersion) { }
}
