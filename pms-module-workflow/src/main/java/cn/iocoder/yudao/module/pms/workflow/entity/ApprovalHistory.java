package cn.iocoder.yudao.module.pms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批历史实体（Story 6）。
 *
 * <p>记录每轮每次操作（节点、操作人、动作、意见、时间戳），支持多轮次追溯。
 * 审批退回后重新提交时复用原审批记录，但本表追加新的历史行（round 字段区分轮次）。</p>
 *
 * <p>{@code action} 取值：{@code SUBMIT / APPROVE / REJECT / WITHDRAW /
 * RESUBMIT / ESCALATE / TIMEOUT}。</p>
 *
 * <p>注：本表无审计字段，不继承 BaseEntity，仅记录操作时间戳 {@code operatedAt}。</p>
 *
 * <p>关联设计文档：§3.5 Story 6 验收 2（行 472-484）、§6.9（行 1616-1628）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("wf_approval_history")
public class ApprovalHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属审批记录ID。 */
    @NotNull(message = "审批记录ID不能为空")
    private Long recordId;

    /** 审批轮次。 */
    @NotNull(message = "审批轮次不能为空")
    private Integer round;

    /** 节点名称。 */
    @NotBlank(message = "节点名称不能为空")
    @Size(max = 64, message = "节点名称长度不能超过 64 个字符")
    private String nodeName;

    /** 操作人ID。 */
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    /** 操作人姓名（冗余）。 */
    @Size(max = 64, message = "操作人姓名长度不能超过 64 个字符")
    private String operatorName;

    /** 动作：SUBMIT/APPROVE/REJECT/WITHDRAW/RESUBMIT/ESCALATE/TIMEOUT。 */
    @NotBlank(message = "动作不能为空")
    @Size(max = 20, message = "动作长度不能超过 20 个字符")
    private String action;

    /** 操作意见。 */
    @Size(max = 500, message = "操作意见长度不能超过 500 个字符")
    private String opinion;

    /** 操作时间。 */
    private LocalDateTime operatedAt;
}
