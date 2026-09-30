package cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query;

/** 执行单主项目回填：仅当未绑定或已绑定同一项目时生效（原子防抢占）。 */
public record ExecutionPrimaryProjectUpdate(Long tenantId, Long id, Long projectId, Long updater) {
}
