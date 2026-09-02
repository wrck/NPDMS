package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_spare_application_reference")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverSpareApplicationReferenceDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long cutoverTaskId;
    private Long projectId;
    private String platformRequestId;
    private String integrationStatus;
    private String externalSystemCode;
    private String externalRequestId;
    private String externalApplicationNo;
    private String launchUrl;
    private String needSnapshot;
    private String requestContextSnapshot;
    private Long currentStatusRevisionId;
    private Integer retryCount;
    private String lastFailureCode;
    private String lastFailureDetail;
    private LocalDateTime lastAttemptAt;
    @Version
    private Integer version;
}
