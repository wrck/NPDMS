package cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult;

import lombok.Data;

/** Processing checkpoint only; this row is not an additional project or business state machine. */
@Data
public class ResultSubscriptionDO {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long planVersionId;
    private Long executionId;
    private String nodeKind;
    private Long nodeId;
    private String nodeKey;
    private Long contractId;
    private String subscriptionKey;
    private Long channelId;
    private String configuration;
    private Long baselineSequence;
    private String phase;
    private String inventoryCursor;
    private Long processedSequence;
    private Long throughSequence;
    private Integer version;
}
