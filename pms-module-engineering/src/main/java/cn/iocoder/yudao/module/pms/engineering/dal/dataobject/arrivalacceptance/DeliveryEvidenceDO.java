package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

@TableName("imp_delivery_evidence")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryEvidenceDO extends BaseBusinessEntity {

    private Long projectId;
    private String sourceRequirement;
    private String sourceObjectType;
    private Long sourceObjectId;
    private Integer currentRevisionNo;
    private String accSyncStatus;
    private LocalDateTime accLastPublishedAt;
    private LocalDateTime accNextRetryAt;
    private Integer accRetryCount;
    private String accLastEventId;
    private String accCorrelationId;
    private String accAcceptedRecordId;
    private String accArchivedRecordId;
}
