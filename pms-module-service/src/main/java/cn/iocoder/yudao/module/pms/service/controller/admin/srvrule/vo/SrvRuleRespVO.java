package cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检规则 Response VO")
@Data
public class SrvRuleRespVO {

    @Schema(description = "规则编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "规则编码，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "R-001")
    private String code;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "标准巡检规则")
    private String name;

    @Schema(description = "规则类型 ONLINE 在线 / OFFLINE 离线", example = "ONLINE")
    private String ruleType;

    @Schema(description = "规则版本号", example = "1.0.0")
    private String ruleVersion;

    @Schema(description = "规则内容")
    private String content;

    @Schema(description = "状态 0草稿 1已发布 2已停用", example = "0")
    private Integer status;

    @Schema(description = "生效时间")
    private LocalDateTime effectiveTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
