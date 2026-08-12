package cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 项目团队成员分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTeamMemberPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "用户编号", example = "1")
    private Long userId;

    @Schema(description = "角色编码", example = "PROJECT_MANAGER")
    private String roleCode;

    @Schema(description = "状态：0启用 1停用", example = "0")
    private Integer status;

}
