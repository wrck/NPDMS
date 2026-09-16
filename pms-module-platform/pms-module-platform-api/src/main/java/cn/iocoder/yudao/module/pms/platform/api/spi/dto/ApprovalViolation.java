package cn.iocoder.yudao.module.pms.platform.api.spi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批状态校验违规项（迁移自源工程 pms-common com.dp.plat.common.dto.ApprovalViolation，
 * TD-P8-005 SPI 配套）。
 *
 * <p>由 pms-module-workflow 的 ApprovalStatusChecker 实现返回，
 * 供 pms-module-project 的 validateExitGate APPROVAL 分支跨模块校验关联审批是否通过。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalViolation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审批记录ID（若存在）。 */
    private Long approvalRecordId;

    /** 审批类型（如 PHASE_EXIT）。 */
    private String approvalType;

    /** 期望状态（APPROVED）。 */
    private String expectedStatus;

    /** 实际状态（PENDING/REJECTED/WITHDRAWN/TIMEOUT 或 null=不存在）。 */
    private String actualStatus;
}