package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CutoverConfigurationRespVO {
    private Long id;
    private String configurationCode;
    private String configurationName;
    private Integer revisionNo;
    private String statusCode;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String changeSummary;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long disabledBy;
    private LocalDateTime disabledAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Map<String, Object> dictionarySnapshot;
    private List<CutoverConfigurationSaveReqVO.DimensionVO> dimensions;
    private List<CutoverConfigurationSaveReqVO.PlanTemplateSectionVO> planTemplateSections;
    private List<CutoverConfigurationSaveReqVO.ItemVO> items;
    private List<CutoverConfigurationSaveReqVO.BindingRuleVO> bindingRules;
    private List<CutoverConfigurationValidationRespVO.ValidationErrorVO> validationErrors;
}
