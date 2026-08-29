package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class CutoverConfigurationSaveReqVO {

    @NotBlank(message = "配置编码不能为空")
    @Size(max = 64, message = "配置编码长度不能超过64个字符")
    private String configurationCode;
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 128, message = "配置名称长度不能超过128个字符")
    private String configurationName;
    @Size(max = 500, message = "变更说明长度不能超过500个字符")
    private String changeSummary;
    @NotNull(message = "字典快照不能为空")
    private Map<String, Object> dictionarySnapshot;
    @Valid
    @NotNull(message = "维度定义不能为空")
    private List<DimensionVO> dimensions = new ArrayList<>();
    @Valid
    @NotNull(message = "方案章节不能为空")
    private List<PlanTemplateSectionVO> planTemplateSections = new ArrayList<>();
    @Valid
    @NotNull(message = "采集项不能为空")
    private List<ItemVO> items = new ArrayList<>();
    @Valid
    @NotNull(message = "绑定规则不能为空")
    private List<BindingRuleVO> bindingRules = new ArrayList<>();

    @Data
    public static class DimensionVO {
        @NotBlank private String code;
        @NotBlank private String name;
        @NotBlank private String dataType;
        @NotBlank private String valueSource;
        @NotBlank private String owner;
        @NotBlank private String contextPath;
        private Boolean enabled = true;
    }

    @Data
    public static class PlanTemplateSectionVO {
        @NotBlank private String stableSectionKey;
        @NotBlank private String title;
        private Integer sortOrder = 0;
        private List<String> cutoverTypeCodes = new ArrayList<>();
        private List<String> levelCodes = new ArrayList<>();
        private Boolean required = false;
    }

    @Data
    public static class ItemVO {
        @NotBlank private String stableItemKey;
        @NotBlank private String itemType;
        @NotBlank private String itemName;
        private String itemDescription;
        @NotBlank private String interfaceFormat;
        private Map<String, Object> interfaceSchema;
        @NotBlank private String feedbackFormat;
        private Boolean required = false;
        private String workMode = "MANUAL";
        private Map<String, Object> externalSourceConfig;
        private String subtableCode;
        private Boolean enabled = true;
        private Integer sortOrder = 0;
    }

    @Data
    public static class BindingRuleVO {
        @NotBlank private String stableRuleKey;
        @NotBlank private String stableItemKey;
        @NotNull private Map<String, Object> dimensionConditions;
        private Integer priority = 0;
        private Boolean enabled = true;
    }
}
