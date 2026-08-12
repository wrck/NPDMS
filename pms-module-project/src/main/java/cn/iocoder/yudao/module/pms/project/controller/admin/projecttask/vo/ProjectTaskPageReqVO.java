package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 项目任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "父任务编号", example = "1")
    private Long parentId;

    @Schema(description = "任务名称，模糊匹配", example = "需求")
    private String name;

    @Schema(description = "任务编码，模糊匹配", example = "TASK")
    private String code;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "负责人编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "执行人编号", example = "2")
    private Long assigneeUserId;

}
