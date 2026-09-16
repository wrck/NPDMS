package cn.iocoder.yudao.module.pms.workflow.vo;

import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalHistory;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 审批详情 VO（Story 6）。
 *
 * <p>审批详情接口返回值，包含审批记录、脱敏后的业务数据、脱敏字段元数据与审批历史。
 * 后端按当前用户在当前节点的字段权限对业务数据脱敏后返回。</p>
 *
 * <p>关联设计文档：§3.5 Story 6 验收 1（行 449-467）、§5.7（行 1094-1121）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDetailVO implements Serializable {

    /** 审批记录。 */
    private ApprovalRecord record;

    /** 业务数据（脱敏后，fieldName → value）。HIDDEN 字段不会出现。 */
    private Map<String, Object> businessData;

    /** 脱敏字段元数据列表（供前端展示脱敏提示）。 */
    private List<MaskedFieldVO> maskedFields;

    /** 审批历史（含所有轮次）。 */
    private List<ApprovalHistory> history;
}
