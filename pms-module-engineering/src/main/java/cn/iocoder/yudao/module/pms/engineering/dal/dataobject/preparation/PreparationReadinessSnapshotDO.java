package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_preparation_readiness_snapshot")
@Data
public class PreparationReadinessSnapshotDO implements Serializable {
    @TableId private Long id;
    private Long preparationId;
    private Integer snapshotNo;
    private String resultCode;
    private Integer ruleVersion;
    private Long projectScopeVersion;
    private Integer inputVersion;
    private Integer preparationVersion;
    private Integer readinessVersion;
    private String itemFactsSnapshot;
    private String fileFactsSnapshot;
    private String sourceFactsSnapshot;
    private String waiverFactsSnapshot;
    private String blockersSnapshot;
    private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
    private String creator;
    private LocalDateTime createTime;
    private Long tenantId;
}
