package cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 实施问题 Response VO（FR-ENG-026）。
 */
@Schema(description = "管理后台 - 实施问题 Response VO")
@Data
public class IssueRespVO {

    @Schema(description = "问题编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "问题编码", example = "IS20260101001")
    private String code;

    @Schema(description = "问题名称", example = "联调阶段业务流量切换超时")
    private String name;

    @Schema(description = "问题描述", example = "主备切换耗时 90s，超过预期 60s")
    private String description;

    @Schema(description = "来源", example = "JOINT_TEST")
    private String source;

    @Schema(description = "严重等级 1低 2中 3高", example = "2")
    private Integer severity;

    @Schema(description = "责任人编号", example = "1024")
    private Long ownerUserId;

    @Schema(description = "整改时限")
    private LocalDateTime deadline;

    @Schema(description = "整改方案", example = "调整 keepalive 探测间隔，优化切换脚本")
    private String solution;

    @Schema(description = "验证标准", example = "切换耗时 ≤ 60s，业务无中断")
    private String verificationStandard;

    @Schema(description = "复测结果", example = "切换耗时 45s，业务无中断")
    private String verifyResult;

    @Schema(description = "验证人编号", example = "1024")
    private Long verifiedBy;

    @Schema(description = "验证时间")
    private LocalDateTime verifiedTime;

    @Schema(description = "状态：0待处理 1整改中 2待验证 3已关闭 4已挂起", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "需协同客户运维确认")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
