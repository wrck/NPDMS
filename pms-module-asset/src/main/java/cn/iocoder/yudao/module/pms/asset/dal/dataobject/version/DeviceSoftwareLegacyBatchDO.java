package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
/** Read-only all-column guard for the approved retirement of V414 batch mirrors. */
@Data @EqualsAndHashCode(callSuper=true)
public class DeviceSoftwareLegacyBatchDO extends DeviceSoftwareChangeBatchDO {
    private String sourceProjectKey, sourceSystem, sourceKey, sourceVersion, sourceChecksum;
    private LocalDateTime sourceReadAt;
    private Long sourceId, sourceProjectId;
    private String sourceCreatedBy, sourceUpdatedBy;
    private LocalDateTime sourceCreatedAt, sourceUpdatedAt;
}
