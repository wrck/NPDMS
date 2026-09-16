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
 * 审批节点实体（Story 6）。
 *
 * <p>一条审批记录（{@link ApprovalRecord}）下挂多个按 {@code nodeOrder} 顺序流转的
 * 审批节点。当前节点通过后激活 {@code nodeOrder+1} 的节点；通过最后节点则将审批记录
 * 状态置为 APPROVED。</p>
 *
 * <p>{@code approverId}（指定审批人）与 {@code approverRole}（审批角色）二选一，
 * 实际处理人记录在 {@code approverActualId}（支持转办场景）。</p>
 *
 * <p>注：本表无审计字段（createBy/updateTime 等），不继承 BaseEntity。</p>
 *
 * <p>关联设计文档：§6.9（行 1600-1614）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("wf_approval_node")
public class ApprovalNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属审批记录ID。 */
    @NotNull(message = "审批记录ID不能为空")
    private Long recordId;

    /** 节点名称。 */
    @NotBlank(message = "节点名称不能为空")
    @Size(max = 64, message = "节点名称长度不能超过 64 个字符")
    private String nodeName;

    /** 节点顺序（从 1 开始）。 */
    @NotNull(message = "节点顺序不能为空")
    private Integer nodeOrder;

    /** 指定审批人ID（与 approverRole 二选一）。 */
    private Long approverId;

    /** 审批角色（多选一时使用）。 */
    @Size(max = 32, message = "审批角色长度不能超过 32 个字符")
    private String approverRole;

    /** 节点状态：PENDING / APPROVED / REJECTED。 */
    @Builder.Default
    private String status = "PENDING";

    /** 实际处理人ID（支持转办场景）。 */
    private Long approverActualId;

    /** 审批意见。 */
    @Size(max = 500, message = "审批意见长度不能超过 500 个字符")
    private String opinion;

    /** 处理时间。 */
    private LocalDateTime operatedAt;

    /** 节点超时时间点。 */
    private LocalDateTime timeoutAt;
}
