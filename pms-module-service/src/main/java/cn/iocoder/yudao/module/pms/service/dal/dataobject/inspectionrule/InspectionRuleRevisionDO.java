package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("srv_inspection_rule_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionRuleRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long ruleId;
    private Integer revisionNo;
    private String statusCode;
    private String ruleNameSnapshot;
    private String inspectionItem;
    private String description;
    private String categoryCode;
    private String categoryNameSnapshot;
    private String severityCode;
    private String severityNameSnapshot;
    private Integer sortOrder;
    private String expectedResultRegex;
    private String thresholdDataType;
    private String thresholdOperator;
    private BigDecimal thresholdValue;
    private String thresholdUnit;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long disabledBy;
    private LocalDateTime disabledAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer currentPublishedMarker;
    @Version
    private Integer version;
}
