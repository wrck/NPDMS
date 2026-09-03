package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("acc_satisfaction_result")
@Data
public class SatisfactionResultDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long collectionTaskId;
    private Long questionnaireId;
    private Long responseId;
    private String collectionKey;
    private Integer resultVersion;
    private BigDecimal score;
    private BigDecimal threshold;
    private Boolean passed;
    private String ruleVersion;
    private String resultStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String archiveStatus;
    private Long archiveActorUserId;
    private Long deliverableSourceVersionId;
    private String archiveFailureCode;
    private Integer archiveRetryCount;
    private Long invalidatedByUserId;
    private LocalDateTime invalidatedAt;
    private String invalidationReasonCode;
    private String invalidationReasonSummary;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
