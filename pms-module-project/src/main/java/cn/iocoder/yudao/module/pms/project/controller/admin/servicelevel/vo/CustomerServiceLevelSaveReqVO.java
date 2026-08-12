package cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - PMS 客户服务等级创建/修改 Request VO")
@Data
public class CustomerServiceLevelSaveReqVO {

    @Schema(description = "服务等级编号", example = "1024")
    private Long id;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "客户不能为空")
    private Long customerId;

    @Schema(description = "服务等级 STRATEGIC/IMPORTANT/STANDARD/GENERAL", requiredMode = Schema.RequiredMode.REQUIRED, example = "STRATEGIC")
    @NotBlank(message = "服务等级不能为空")
    @Size(max = 16, message = "服务等级长度不能超过 16 个字符")
    private String level;

    @Schema(description = "生效开始日期", example = "2026-01-01")
    private LocalDate validFrom;

    @Schema(description = "生效结束日期", example = "2026-12-31")
    private LocalDate validTo;

    @Schema(description = "响应时间（小时）", example = "4")
    private Integer responseTimeHours;

    @Schema(description = "是否主动服务", example = "false")
    private Boolean proactiveService;

    @Schema(description = "备注", example = "战略客户")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

}
