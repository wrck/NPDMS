package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
@lombok.Data @lombok.EqualsAndHashCode(callSuper=true)
public class DirectDeviceBaseRow extends cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity {
    private String sn;
    private String name;
    private String productName;
    private String productModel;
    private String productCode;
    private String packageNo;
    private String contractNo;
    private String status;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private String syncStatus;
    private String remark;
    private java.time.LocalDateTime shipmentTime;
    private java.time.LocalDateTime syncedAt;
    private Long shipmentRecordId;
    private Long projectAssignmentVersion;
    private Long customerAssignmentVersion;
}
