package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "模板规则匹配预演；省略字段为未知，显式 null 为已知空值")
@Data
public class ProjectTemplateMatchPreviewReqVO {
    @NotNull
    private Map<String, JsonNode> facts = new LinkedHashMap<>();
}
