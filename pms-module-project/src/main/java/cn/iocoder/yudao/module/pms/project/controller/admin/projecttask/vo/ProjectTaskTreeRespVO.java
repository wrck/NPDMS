package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - PMS 项目任务树 Response VO")
@Data
public class ProjectTaskTreeRespVO {

    @Schema(description = "任务编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "父任务编号", example = "1")
    private Long parentId;

    @Schema(description = "根任务编号", example = "1")
    private Long rootId;

    @Schema(description = "物化路径", example = "/1/1024/")
    private String path;

    @Schema(description = "路径深度", example = "0")
    private Integer depth;

    @Schema(description = "同级排序号", example = "0")
    private Integer sort;

    @Schema(description = "任务名称", example = "需求调研")
    private String name;

    @Schema(description = "任务编码", example = "TASK-001")
    private String code;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "进度 0-100", example = "0")
    private Integer progress;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "执行人用户编号", example = "2")
    private Long assigneeUserId;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "子任务列表")
    private List<ProjectTaskTreeRespVO> children;

}
