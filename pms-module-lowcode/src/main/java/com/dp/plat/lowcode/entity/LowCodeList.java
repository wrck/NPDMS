package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码列表配置实体。
 *
 * <p>存储列表设计器产出的 JSON Schema 配置，包含列定义（columns）、筛选条件（filters）、
 * 操作按钮（operations）、分页配置（pagination）和搜索 API（searchApi）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_list")
public class LowCodeList extends BaseEntity {

    /** 列表编码（唯一标识） */
    @NotBlank(message = "列表编码不能为空")
    @Size(max = 64, message = "列表编码长度不能超过 64 个字符")
    private String code;

    /** 列表名称 */
    @NotBlank(message = "列表名称不能为空")
    @Size(max = 128, message = "列表名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 列表配置 JSON Schema（columns/filters/operations/pagination/searchApi） */
    @NotBlank(message = "列表配置不能为空")
    private String listConfig;

    /** 版本号（乐观锁） */
    @Version
    @Builder.Default
    private Integer version = 1;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "DRAFT";

    /** 业务类型 */
    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;
}
