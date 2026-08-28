package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class DynamicFormRevisionPatchReqVO {
    private JsonNode formConfJson;
    private JsonNode formRulesJson;
    @NotBlank private String engineCode;
    @NotBlank private String designerVersion;
    @NotBlank private String rendererVersion;
}
