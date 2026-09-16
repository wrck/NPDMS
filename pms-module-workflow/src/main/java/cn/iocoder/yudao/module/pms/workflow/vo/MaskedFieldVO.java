package cn.iocoder.yudao.module.pms.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 脱敏字段元数据 VO（Story 6）。
 *
 * <p>审批详情返回值 {@code maskedFields} 数组的单项，描述某个字段的脱敏配置与脱敏后的值，
 * 供前端展示脱敏提示图标（ⓘ）与说明。</p>
 *
 * <p>关联设计文档：§5.7 Story 6 验收 1（行 1114-1117）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaskedFieldVO implements Serializable {

    /** 字段名。 */
    private String fieldName;

    /** 权限：VISIBLE / MASKED / HIDDEN。 */
    private String permission;

    /** 脱敏后的值（字符串形式）。 */
    private String maskedValue;

    /** 脱敏规则：phone-mask / amount-mask / email-mask / custom。 */
    private String maskPattern;
}
