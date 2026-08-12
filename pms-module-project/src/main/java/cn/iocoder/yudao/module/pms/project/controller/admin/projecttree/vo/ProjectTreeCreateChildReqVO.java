package cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目树创建子项目 Request VO")
@Data
public class ProjectTreeCreateChildReqVO {

    @Schema(description = "父项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "父项目编号不能为空")
    private Long parentId;

    @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PMS202401002")
    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64, message = "项目编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "子项目A")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    @Schema(description = "同级排序号", example = "0")
    private Integer sort;

    @Schema(description = "项目分类", example = "战略")
    @Size(max = 64, message = "项目分类长度不能超过 64 个字符")
    private String category;

    @Schema(description = "是否重大项目", example = "false")
    private Boolean majorProjectFlag;

    @Schema(description = "项目经理编号", example = "1")
    private Long managerUserId;

}
