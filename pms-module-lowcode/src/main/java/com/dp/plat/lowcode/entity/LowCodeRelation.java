package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码实体关联关系。
 *
 * <p>支持四种关联类型：
 * <ul>
 *   <li>ONE_TO_ONE / ONE_TO_MANY / MANY_TO_ONE：通过 from_field_name 外键实现</li>
 *   <li>MANY_TO_MANY：通过 junction_table 中间表实现</li>
 * </ul>
 * 支持自关联（from_entity_id == to_entity_id）和级联删除策略（CASCADE/SET_NULL/RESTRICT）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_relation")
public class LowCodeRelation extends BaseEntity {

    @NotNull(message = "源实体ID不能为空")
    private Long fromEntityId;

    @NotNull(message = "目标实体ID不能为空")
    private Long toEntityId;

    /** 关联类型: ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE/MANY_TO_MANY */
    @NotBlank(message = "关联类型不能为空")
    @Size(max = 16, message = "关联类型长度不能超过 16 个字符")
    private String relationType;

    @NotBlank(message = "源端外键字段名不能为空")
    @Size(max = 64, message = "源端外键字段名长度不能超过 64 个字符")
    private String fromFieldName;

    @Size(max = 64, message = "目标端外键字段名长度不能超过 64 个字符")
    private String toFieldName;

    @Size(max = 64, message = "反向关联名称长度不能超过 64 个字符")
    private String reverseName;

    /** 多对多中间表名（仅 MANY_TO_MANY 使用） */
    @Size(max = 64, message = "中间表名长度不能超过 64 个字符")
    private String junctionTable;

    /** 级联删除策略: CASCADE/SET_NULL/RESTRICT */
    @NotBlank(message = "级联删除策略不能为空")
    @Size(max = 16, message = "级联删除策略长度不能超过 16 个字符")
    @Builder.Default
    private String onDelete = "RESTRICT";

    /** 级联更新策略: CASCADE/RESTRICT */
    @NotBlank(message = "级联更新策略不能为空")
    @Size(max = 16, message = "级联更新策略长度不能超过 16 个字符")
    @Builder.Default
    private String onUpdate = "RESTRICT";
}
