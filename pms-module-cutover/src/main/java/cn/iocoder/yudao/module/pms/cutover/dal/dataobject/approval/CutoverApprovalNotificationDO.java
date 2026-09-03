package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_notification")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalNotificationDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long approvalInstanceId;
    private Long approvalNodeId;
    private Long recipientUserId;
    private String deliveryKey;
    private String correlationId;
    private String templateCode;
    private String channelCode;
    private String statusCode;
    private Long messageId;
    private String providerReferenceId;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime sentAt;
    @Version
    private Integer version;
}
