package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String dictionarySnapshot;
    private String dimensionDefinitionSnapshot;
    private String planTemplateSectionSnapshot;
    private String navigationRuleSnapshot;
    private String validationResultSnapshot;
    private String changeSummary;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long disabledBy;
    private LocalDateTime disabledAt;
}
