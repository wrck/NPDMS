package cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 客户服务等级 Response VO")
@Data
public class CustomerServiceLevelRespVO {

    @Schema(description = "服务等级编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long customerId;

    @Schema(description = "客户名称", example = "阿里巴巴")
    private String customerName;

    @Schema(description = "服务等级 STRATEGIC/IMPORTANT/STANDARD/GENERAL", requiredMode = Schema.RequiredMode.REQUIRED, example = "STRATEGIC")
    private String level;

    @Schema(description = "生效开始日期", example = "2026-01-01")
    private LocalDate validFrom;

    @Schema(description = "生效结束日期", example = "2026-12-31")
    private LocalDate validTo;

    @Schema(description = "状态：0草稿 1已生效 2已停用 3已归档", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "响应时间（小时）", example = "4")
    private Integer responseTimeHours;

    @Schema(description = "是否主动服务", example = "false")
    private Boolean proactiveService;

    @Schema(description = "备注", example = "战略客户")
    private String remark;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
