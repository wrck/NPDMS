package cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检问题 Response VO")
@Data
public class SrvIssueRespVO {

    @Schema(description = "问题编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long taskId;

    @Schema(description = "问题编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "ISS-001")
    private String code;

    @Schema(description = "问题名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "端口异常")
    private String name;

    @Schema(description = "问题描述")
    private String description;

    @Schema(description = "严重程度 H 高 / M 中 / L 低", example = "M")
    private String severity;

    @Schema(description = "责任人", example = "300")
    private Long ownerUserId;

    @Schema(description = "整改截止时间")
    private LocalDateTime deadline;

    @Schema(description = "整改方案")
    private String solution;

    @Schema(description = "验证结果")
    private String verifyResult;

    @Schema(description = "验证人", example = "300")
    private Long verifiedBy;

    @Schema(description = "验证时间")
    private LocalDateTime verifiedTime;

    @Schema(description = "状态 0待分派 1已分派 2待验证 3已关闭 4已取消", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
