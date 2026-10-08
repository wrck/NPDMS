package cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query;

/** Bind only the locked source identity, within the verified company and tenant. */
public record ExecutionPrimaryProjectUpdate(Long tenantId, Long id, Long projectId, Long updater,
        String companyCode, Long companyId, String executionNo, String sourceSystem) {
}
