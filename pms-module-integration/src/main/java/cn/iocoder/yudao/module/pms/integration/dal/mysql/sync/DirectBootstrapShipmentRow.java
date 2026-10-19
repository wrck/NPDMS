package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
@lombok.Data @lombok.EqualsAndHashCode(callSuper=true)
public class DirectBootstrapShipmentRow extends cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO {
    private Long id;
    private String deviceSn,sourceKey,contractNo,packageNo,productCode,sourceSystem;
    private java.time.LocalDateTime shipmentTime;
}
