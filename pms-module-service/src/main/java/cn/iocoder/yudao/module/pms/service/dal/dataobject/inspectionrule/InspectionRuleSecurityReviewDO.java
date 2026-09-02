package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("srv_inspection_rule_security_review")
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionRuleSecurityReviewDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String reviewReference;
    private Long revisionId;
    private String contentDigest;
    private Long reviewedBy;
    private String permissionCode;
    private String authorizationType;
    private String authorizationSourceId;
    private String conclusionCode;
    private LocalDateTime reviewedAt;
    @Version
    private Integer version;
}
