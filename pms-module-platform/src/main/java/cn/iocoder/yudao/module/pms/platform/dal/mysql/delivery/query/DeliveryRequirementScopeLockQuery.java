package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/**
 * 项目范围维度的模板冻结要求锁定查询：stageCode 与 taskCode 互斥使用（单场景一查询），
 * 二者皆空 = 锁定项目全部模板冻结要求。
 */
public record DeliveryRequirementScopeLockQuery(Long tenantId, Long projectId, String stageCode, String taskCode) {

    public static DeliveryRequirementScopeLockQuery byStage(Long tenantId, Long projectId, String stageCode) {
        return new DeliveryRequirementScopeLockQuery(tenantId, projectId, stageCode, null);
    }

    public static DeliveryRequirementScopeLockQuery all(Long tenantId, Long projectId) {
        return new DeliveryRequirementScopeLockQuery(tenantId, projectId, null, null);
    }
}
