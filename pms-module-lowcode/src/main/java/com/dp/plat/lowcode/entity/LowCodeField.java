package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码实体字段定义。
 *
 * <p>描述实体的每个字段元数据：名称、类型、长度、约束（主键/索引/唯一/可空）等。
 * DDL 生成器基于此定义生成 CREATE TABLE / ALTER TABLE 语句。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_field")
public class LowCodeField extends BaseEntity {

    @NotNull(message = "所属实体ID不能为空")
    private Long entityId;

    @NotBlank(message = "字段名不能为空")
    @Size(max = 64, message = "字段名长度不能超过 64 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字段名必须小写字母开头，只能包含小写字母、数字和下划线")
    private String name;

    @NotBlank(message = "字段显示名不能为空")
    @Size(max = 128, message = "字段显示名长度不能超过 128 个字符")
    private String label;

    /** 字段类型: STRING/INTEGER/DECIMAL/BOOLEAN/DATE/DATETIME/TEXT/LONG */
    @NotBlank(message = "字段类型不能为空")
    @Size(max = 32, message = "字段类型长度不能超过 32 个字符")
    private String fieldType;

    /** 长度（STRING/DECIMAL 用） */
    private Integer length;

    /** 小数位数（DECIMAL 用） */
    private Integer scale;

    /** 是否可空: 0否 1是 */
    @NotNull(message = "是否可空不能为空")
    @Builder.Default
    private Integer nullable = 1;

    /** 是否主键: 0否 1是 */
    @NotNull(message = "是否主键不能为空")
    @Builder.Default
    private Integer primaryKey = 0;

    /** 是否索引: 0否 1是 */
    @NotNull(message = "是否索引不能为空")
    @Builder.Default
    private Integer indexed = 0;

    /** 是否唯一: 0否 1是 */
    @NotNull(message = "是否唯一不能为空")
    @Builder.Default
    private Integer uniqueFlag = 0;

    @Size(max = 256, message = "默认值长度不能超过 256 个字符")
    private String defaultValue;

    @NotNull(message = "排序不能为空")
    @Builder.Default
    private Integer sortOrder = 0;
}
