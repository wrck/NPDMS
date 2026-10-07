package cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ProjectLegacySnapshotDO {
    private Long id;
    private Long tenantId;
    private Long sourceId;
    private String batchKey;
    private String checksum;
    private LocalDateTime sourceReadAt;
    private LocalDateTime capturedAt;
    private Long operatorUserId;
    private String domainCountsJson;
    private Integer recordCount;
}
