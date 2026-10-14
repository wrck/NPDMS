package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_cutover_configuration_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverConfigurationRevisionDO extends BaseBusinessEntity {
    private String configurationCode;
    private String configurationName;
    private Integer revisionNo;
    private String statusCode;
    @TableField("effective_start_time")
    private LocalDateTime effectiveFrom;
    @TableField("effective_end_time")
    private LocalDateTime effectiveTo;
    @TableField(exist = false)
    private String dictionarySnapshot;
    @TableField(exist = false)
    private String dimensionDefinitionSnapshot;
    @TableField(exist = false)
    private String planTemplateSectionSnapshot;
    @TableField(exist = false)
    private String navigationRuleSnapshot;
    @TableField(exist = false)
    private String validationResultSnapshot;
    private String changeSummary;
    @TableField("publish_user_id")
    private Long publishedBy;
    @TableField("publish_time")
    private LocalDateTime publishedAt;
    @TableField("disable_user_id")
    private Long disabledBy;
    @TableField("disable_time")
    private LocalDateTime disabledAt;
    private String navigationTargetCode;
    private Boolean validationValidFlag;
    private Integer currentValidationNo;
    private LocalDateTime validateTime;
    private Long validateUserId;
}
