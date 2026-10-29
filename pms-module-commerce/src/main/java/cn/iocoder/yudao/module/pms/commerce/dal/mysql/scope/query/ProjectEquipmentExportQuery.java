package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

@lombok.Builder(toBuilder = true)
public record ProjectEquipmentExportQuery(Long tenantId, Long projectId, Long afterId, int limit,
        java.util.List<String> companyCodes, java.util.List<Long> orderLineProjectIds) { }
