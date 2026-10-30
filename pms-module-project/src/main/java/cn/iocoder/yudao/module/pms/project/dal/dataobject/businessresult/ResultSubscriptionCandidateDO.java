package cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult;

import lombok.Data;

/** 可更新的候选索引，不是已采纳证据或业务完成历史。 */
@Data
public class ResultSubscriptionCandidateDO {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long subscriptionId;
    private String objectId;
    private String resultId;
    private Long formationSequence;
    private Long observedSequence;
    private String observation;
}
