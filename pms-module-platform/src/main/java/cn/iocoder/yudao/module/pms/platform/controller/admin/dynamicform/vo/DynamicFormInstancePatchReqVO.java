package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class DynamicFormInstancePatchReqVO {
    @NotNull
    private JsonNode values;
}
