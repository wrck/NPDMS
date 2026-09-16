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
 * 低代码标签页配置实体。
 *
 * <p>存储标签页设计器产出的 JSON 配置，包含 tabs 数组，
 * 每个 tab 含 title（标签标题）、pageCode（引用的页面编码）、type（页面类型）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_tab")
public class LowCodeTab extends BaseEntity {

    /** 标签页编码（唯一标识） */
    @NotBlank(message = "标签页编码不能为空")
    @Size(max = 64, message = "标签页编码长度不能超过 64 个字符")
    private String code;

    /** 标签页名称 */
    @NotBlank(message = "标签页名称不能为空")
    @Size(max = 128, message = "标签页名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 标签页配置 JSON（tabs 数组，每个 tab 含 title/pageCode/type） */
    @NotBlank(message = "标签页配置不能为空")
    private String tabConfig;

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
