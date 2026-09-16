package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码实体定义。
 *
 * <p>存储可视化实体设计器产出的实体元数据，包含编码、名称、物理表名等。
 * 字段定义见 {@link LowCodeField}，关联关系见 {@link LowCodeRelation}。
 * 支持状态流转：DRAFT → PUBLISHED → ARCHIVED。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_entity")
public class LowCodeEntity extends BaseEntity {

    @NotBlank(message = "实体编码不能为空")
    @Size(max = 64, message = "实体编码长度不能超过 64 个字符")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "实体编码必须以字母开头，只能包含字母、数字和下划线")
    private String code;

    @NotBlank(message = "实体名称不能为空")
    @Size(max = 128, message = "实体名称长度不能超过 128 个字符")
    private String name;

    @NotBlank(message = "物理表名不能为空")
    @Size(max = 64, message = "物理表名长度不能超过 64 个字符")
    @Pattern(regexp = "^pms_lc_[a-z][a-z0-9_]*$", message = "物理表名必须以 pms_lc_ 开头，小写字母+数字+下划线")
    private String tableName;

    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;

    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "DRAFT";

    @Version
    @Builder.Default
    private Integer version = 1;
}
