package cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail;
import lombok.Data;
@Data
public class ProjectLegacyRecordDO {
    private Long id;
    private Long tenantId;
    private Long snapshotId;
    private String domainCode;
    private String sourceTable;
    private String sourceKey;
    private String parentDomain;
    private String parentSourceKey;
    private String sourceUpdatedAt;
    private String checksum;
    private String payloadJson;
    private String redactedFieldsJson;
}
