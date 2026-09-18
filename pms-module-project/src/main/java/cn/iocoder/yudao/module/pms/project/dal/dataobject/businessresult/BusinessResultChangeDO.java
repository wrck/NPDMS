package cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult;

import lombok.Data;

/** Append-only delivery metadata. The Owner remains the authority for current business state. */
@Data
public class BusinessResultChangeDO {
    private Long tenantId;
    private Long channelId;
    private Long sequenceNo;
    private String sourceEventId;
    private String notificationId;
    private String objectId;
    private String resultId;
    private Integer formationMarker;
    private String payload;
}
