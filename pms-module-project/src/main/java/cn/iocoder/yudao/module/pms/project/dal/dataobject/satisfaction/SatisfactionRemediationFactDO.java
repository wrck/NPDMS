package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("acc_satisfaction_remediation_fact")
@Data
public class SatisfactionRemediationFactDO {
    @TableId private Long id;
    private Long tenantId;
    private Long priorResultId;
    private Integer remediationRevisionNo;
    private String remediationRequestId;
    private String evidenceSummary;
    private String evidenceFileFactVersion;
    private Long completedBy;
    private LocalDateTime completedAt;
    private Long factVersion;
    private String creator;
    private LocalDateTime createTime;
}
