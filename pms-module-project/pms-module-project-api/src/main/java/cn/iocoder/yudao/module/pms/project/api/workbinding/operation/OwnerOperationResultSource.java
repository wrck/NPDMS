package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

/** Owner-side committed-fact adapter. A missing object returns no fabricated success fact. */
public interface OwnerOperationResultSource {
    boolean supports(String aggregateType);
    ProjectOperationResult current(Long tenantId, Long projectId, String aggregateType, Long objectId);
}
