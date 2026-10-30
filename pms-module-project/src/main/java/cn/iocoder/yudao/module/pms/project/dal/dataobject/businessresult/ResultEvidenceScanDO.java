package cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult;

import lombok.Data;

/** 一次完整结果集合判断；COLLECTING可推进，形成结论后只读保留。 */
@Data
public class ResultEvidenceScanDO {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long subscriptionId;
    private Integer subscriptionVersion;
    private Long throughSequence;
    private Long afterCandidateId;
    private String accumulator;
    private String status;
    private Integer version;
}
