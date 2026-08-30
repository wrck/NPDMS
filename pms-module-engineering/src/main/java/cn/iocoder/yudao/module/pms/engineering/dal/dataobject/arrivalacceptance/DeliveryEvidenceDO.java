package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("imp_delivery_evidence")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryEvidenceDO extends TenantBaseDO {

    @TableId
    private Long id;
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
    @Version
    private Integer version;
}
