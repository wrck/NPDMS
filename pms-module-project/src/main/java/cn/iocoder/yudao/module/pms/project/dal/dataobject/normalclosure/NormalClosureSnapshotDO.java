package cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("acc_closure_gate_snapshot")
@Data @EqualsAndHashCode(callSuper = true)
public class NormalClosureSnapshotDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long projectId;
    private Integer projectVersion;
    private Long treeVersion;
    private String fromStage;
    private String closureType;
    private Integer ruleRevision;
    private Boolean passed;
    private String evidence;
    private String sourceVector;
    private String sourceDigest;
    private Long checkedBy;
    private LocalDateTime checkedAt;
}
