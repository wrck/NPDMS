package cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 转维保 Response VO")
@Data
public class MaintenanceTransitionRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "转维保编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "MT-001")
    private String code;

    @Schema(description = "转维保名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX设备转维保")
    private String name;

    @Schema(description = "设备编号", example = "200")
    private Long equipmentId;

    @Schema(description = "关联验收编号", example = "300")
    private Long acceptanceId;

    @Schema(description = "维保年限（年）", example = "3")
    private Integer maintenanceYears;

    @Schema(description = "维保开始日期")
    private LocalDate startDate;

    @Schema(description = "维保结束日期")
    private LocalDate endDate;

    @Schema(description = "生效操作人", example = "500")
    private Long activateUserId;

    @Schema(description = "生效时间")
    private LocalDateTime activateTime;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "续保年限（年）", example = "1")
    private Integer renewYears;

    @Schema(description = "续保结束日期")
    private LocalDate renewEndDate;

    @Schema(description = "状态 0草稿 1待生效 2生效中 3已过期 4已续保", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
