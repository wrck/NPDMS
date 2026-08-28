package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DynamicFormTemplateCreateReqVO {
    @NotBlank @Size(max = 64)
    private String templateCode;
    @NotBlank @Size(max = 128)
    private String templateName;
    @NotBlank @Size(max = 64)
    private String categoryCode;
    @Size(max = 512)
    private String description;
}
