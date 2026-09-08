package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
/** PM-03: copy creates a new identity and only a draft; original defaults are preserved. */
@Data
public class ProjectTemplateCopyReqVO {
    @NotBlank @Size(max=64) private String code;
    @NotBlank @Size(max=128) private String name;
    @Min(0) private Integer sourceRevisionNo;
}
