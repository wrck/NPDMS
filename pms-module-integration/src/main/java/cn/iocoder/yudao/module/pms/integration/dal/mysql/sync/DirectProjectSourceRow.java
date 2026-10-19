package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import lombok.Data;
@Data
public class DirectProjectSourceRow {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String sourceProjectKey;
    private String sourceContractNo;
    private Long currentSnapshotId;
    private Long version;
}
