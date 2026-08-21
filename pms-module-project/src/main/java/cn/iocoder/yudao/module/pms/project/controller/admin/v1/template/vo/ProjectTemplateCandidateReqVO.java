package cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Schema(description = "管理后台 - PM-03 项目模板候选查询")
@Data
public class ProjectTemplateCandidateReqVO {

    @NotBlank private String signingMethodCode;
    @NotBlank private String projectCategoryCode;
    @NotBlank private String implementationModeCode;
    private String majorProjectLevelCode;
    @NotBlank private String businessSceneCode;
    @NotNull @Positive private Long customerId;
    @NotNull @Positive private Long officeId;
    @NotNull @Positive private Long implementationLocationId;

    public TemplateMatchCriteria toCriteria() {
        return new TemplateMatchCriteria(signingMethodCode, projectCategoryCode, implementationModeCode,
                majorProjectLevelCode, businessSceneCode, customerId, officeId, implementationLocationId);
    }
}
