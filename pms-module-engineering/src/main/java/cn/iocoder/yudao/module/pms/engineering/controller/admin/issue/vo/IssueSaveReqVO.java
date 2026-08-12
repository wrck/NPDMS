package cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 实施问题新增/修改 Request VO（FR-ENG-026）。
 */
@Schema(description = "管理后台 - 实施问题新增/修改 Request VO")
@Data
public class IssueSaveReqVO {

    @Schema(description = "问题编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "问题编码，项目内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "IS20260101001")
    @NotBlank(message = "问题编码不能为空")
    @Size(max = 64, message = "问题编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "问题名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "联调阶段业务流量切换超时")
    @NotBlank(message = "问题名称不能为空")
    @Size(max = 255, message = "问题名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "问题描述", example = "主备切换耗时 90s，超过预期 60s")
    @Size(max = 2000, message = "问题描述长度不能超过 2000 个字符")
    private String description;

    @Schema(description = "来源 INSTALLATION 安装 / CONFIGURATION 配置 / JOINT_TEST 联调 / OTHER 其他", example = "JOINT_TEST")
    @Size(max = 32, message = "来源长度不能超过 32 个字符")
    private String source;

    @Schema(description = "严重等级 1低 2中 3高", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "严重等级不能为空")
    private Integer severity;

    @Schema(description = "责任人编号", example = "1024")
    private Long ownerUserId;

    @Schema(description = "整改时限", example = "2026-01-10 18:00:00")
    private LocalDateTime deadline;

    @Schema(description = "整改方案", example = "调整 keepalive 探测间隔，优化切换脚本")
    @Size(max = 2000, message = "整改方案长度不能超过 2000 个字符")
    private String solution;

    @Schema(description = "验证标准", example = "切换耗时 ≤ 60s，业务无中断")
    @Size(max = 1000, message = "验证标准长度不能超过 1000 个字符")
    private String verificationStandard;

    @Schema(description = "备注", example = "需协同客户运维确认")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
