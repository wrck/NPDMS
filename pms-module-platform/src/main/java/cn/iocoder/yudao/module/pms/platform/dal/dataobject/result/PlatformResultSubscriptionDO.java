package cn.iocoder.yudao.module.pms.platform.dal.dataobject.result;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 统一模型结果订阅：选择策略冻结于建立时，轮次以形成序号为边界。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_plat_result_subscription")
public class PlatformResultSubscriptionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String subscriptionCode;
    private String subscriberKind;
    private String subscriberNodeKey;
    private String resultType;
    private String ownerModule;
    private String entityType;
    private String expectedObjectIds;
    private String acquisition;
    private String selection;
    private String validityPolicy;
    private String pinnedResultId;
    private Long roundNo;
    private Long formationBaseline;
    private String projectStableRef;
    private String planVersion;
    private String ruleVersion;
    private String status;
    private Long lastExamined;
    private Long lastEligible;
    private String missingObjects;
    private Integer version;
}
