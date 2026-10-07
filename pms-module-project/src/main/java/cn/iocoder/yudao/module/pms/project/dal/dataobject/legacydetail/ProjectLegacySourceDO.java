package cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail;
import lombok.Data;
@Data
public class ProjectLegacySourceDO {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String sourceProjectKey;
    private String sourceContractNo;
    private Long currentSnapshotId;
    private Long version;
}
