package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** Gate状态精确CAS。 */
public record ProjectGateStatusUpdate(Long tenantId, Long gateId, Integer expectedVersion,
                                      String expectedStatus, String targetStatus, String updater) {
}
