package cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检任务 Response VO")
@Data
public class SrvTaskRespVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "设备编号", example = "200")
    private Long equipmentId;

    @Schema(description = "巡检任务编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "T-001")
    private String code;

    @Schema(description = "巡检任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机巡检")
    private String name;

    @Schema(description = "巡检方式 ONLINE 在线 / OFFLINE 离线", example = "ONLINE")
    private String inspectionMode;

    @Schema(description = "来源 PROJECT 项目 / PLAN 服务计划 / MANUAL 手工", example = "MANUAL")
    private String sourceType;

    @Schema(description = "来源业务编号", example = "300")
    private Long sourceId;

    @Schema(description = "计划巡检时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "实际巡检时间")
    private LocalDateTime actualTime;

    @Schema(description = "状态 0草稿 1待执行 2执行中 3待确认 4已完成 5已取消", example = "0")
    private Integer status;

    @Schema(description = "设备账号有效性检查结果")
    private String accountCheckResult;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
