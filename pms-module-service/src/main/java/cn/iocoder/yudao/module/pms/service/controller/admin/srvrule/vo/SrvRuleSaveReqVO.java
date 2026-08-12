package cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检规则创建/修改 Request VO")
@Data
public class SrvRuleSaveReqVO {

    @Schema(description = "规则编号", example = "1024")
    private Long id;

    @Schema(description = "规则编码，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "R-001")
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "标准巡检规则")
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "规则类型 ONLINE 在线 / OFFLINE 离线", example = "ONLINE")
    private String ruleType;

    @Schema(description = "规则版本号", example = "1.0.0")
    private String ruleVersion;

    @Schema(description = "规则内容（CLI命令、解析表达式、阈值、严重级别等）")
    private String content;

    @Schema(description = "状态 0草稿 1已发布 2已停用", example = "0")
    private Integer status;

    @Schema(description = "生效时间")
    private LocalDateTime effectiveTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
