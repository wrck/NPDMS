package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Root creation preview: editable facts only; organization codes and creation defaults are owner-resolved. */
@Data
public class ProjectMatchTemplatesReqVO {
    @NotBlank private String projectName;
    private String customerCode;
    private String implementationLocation;
    @NotBlank private String signingMethod;
    @NotBlank private String projectCategory;
    @NotBlank private String implementationMode;
    @NotNull @Positive private Long orderOfficeCompanyId;
    @NotNull @Positive private Long orderOfficeDepartmentId;
}
