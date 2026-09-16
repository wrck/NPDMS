package cn.iocoder.yudao.module.pms.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import cn.iocoder.yudao.module.pms.workflow.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 审批敏感字段权限实体（Story 6）。
 *
 * <p>按审批节点 + 业务实体 + 字段维度配置字段可见性。</p>
 * <ul>
 *   <li>{@code VISIBLE}：原值返回</li>
 *   <li>{@code HIDDEN}：返回 null（字段不出现在详情中）</li>
 *   <li>{@code MASKED}：按 {@code maskPattern} 脱敏后返回</li>
 * </ul>
 *
 * <p>{@code maskPattern} 取值：{@code phone-mask / amount-mask / email-mask / custom}；
 * 当为 {@code custom} 时使用 {@code customPattern}（正则）替换。</p>
 *
 * <p>关联设计文档：§2.2 ApprovalFieldPermission（行 233-243）、§3.5 Story 6 验收 1、§6.9。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wf_approval_field_permission")
public class ApprovalFieldPermission extends BaseEntity {

    /** 关联审批节点ID（或节点模板）。 */
    @NotNull(message = "审批节点ID不能为空")
    private Long approvalNodeId;

    /** 业务实体类名（如 Deliverable）。 */
    @NotBlank(message = "业务实体类型不能为空")
    @Size(max = 128, message = "业务实体类型长度不能超过 128 个字符")
    private String entityType;

    /** 字段名。 */
    @NotBlank(message = "字段名不能为空")
    @Size(max = 64, message = "字段名长度不能超过 64 个字符")
    private String fieldName;

    /** 权限：VISIBLE / MASKED / HIDDEN。 */
    @Builder.Default
    private String permission = "VISIBLE";

    /** 脱敏规则：phone-mask / amount-mask / email-mask / custom。 */
    @Size(max = 64, message = "脱敏规则长度不能超过 64 个字符")
    private String maskPattern;

    /** 自定义正则（当 maskPattern=custom 时使用）。 */
    @Size(max = 128, message = "自定义正则长度不能超过 128 个字符")
    private String customPattern;

    /** 乐观锁版本号（MyBatis-Plus @Version）。 */
    @Version
    private Integer version;
}
