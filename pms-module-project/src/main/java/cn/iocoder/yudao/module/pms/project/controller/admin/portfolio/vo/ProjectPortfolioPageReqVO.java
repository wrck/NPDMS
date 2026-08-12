package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 项目组合分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPortfolioPageReqVO extends PageParam {

    @Schema(description = "组合编码，模糊匹配", example = "PF")
    private String code;

    @Schema(description = "组合名称，模糊匹配", example = "战略")
    private String name;

    @Schema(description = "状态：0草稿 1已发布 2已归档", example = "0")
    private Integer status;

    @Schema(description = "成员类型 STATIC 静态 / DYNAMIC 动态", example = "STATIC")
    private String memberType;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

}
