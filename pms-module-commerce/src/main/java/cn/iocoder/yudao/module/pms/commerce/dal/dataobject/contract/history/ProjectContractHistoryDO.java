package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** Immutable original business history; no current authority or permission is derived from this entity. */
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("com_project_contract_history")
public class ProjectContractHistoryDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private Long contractId;
    private String sourceProjectKey;
    private Long sourceProjectGroupId;
    private String contractNo;
    private String projectGroupCode;
    private Long sourceRowId;
    private String sourceRevisionHash;
    private String sourceSystem;
    private String sourceRecordKey;
    private String sourceCreatedBy;
    private java.time.LocalDateTime sourceCreatedAt;
    private String sourceUpdatedBy;
    private java.time.LocalDateTime sourceUpdatedAt;
}
