package cn.iocoder.yudao.module.pms.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 审批统计 VO（Story 6）。
 *
 * <p>按状态聚合当前用户/项目的审批数量，用于审批中心首页看板。</p>
 *
 * <p>关联设计文档：§5.7 statistics 端点。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalStatisticsVO implements Serializable {

    /** 待办数（PENDING）。 */
    private Long pendingCount;

    /** 已通过数（APPROVED）。 */
    private Long approvedCount;

    /** 已退回数（REJECTED）。 */
    private Long rejectedCount;

    /** 已撤回数（WITHDRAWN）。 */
    private Long withdrawnCount;

    /** 已超时数（TIMEOUT）。 */
    private Long timeoutCount;

    /** 总数。 */
    private Long totalCount;
}
