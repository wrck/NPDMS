package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 项目分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageReqVO extends PageParam {

    @Schema(description = "项目编码，模糊匹配", example = "PMS")
    private String code;

    @Schema(description = "项目名称，模糊匹配", example = "项目")
    private String name;

    @Schema(description = "客户编号", example = "2048")
    private Long customerId;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "项目类型", example = "实施")
    private String projectType;

    @Schema(description = "项目分类", example = "战略")
    private String category;

    @Schema(description = "是否重大项目", example = "false")
    private Boolean majorProjectFlag;

    @Schema(description = "项目经理编号", example = "1")
    private Long managerUserId;

    @Schema(description = "父项目编号", example = "1")
    private Long parentId;

    @Schema(description = "根项目编号", example = "1")
    private Long rootId;

}
