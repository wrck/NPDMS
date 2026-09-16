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
 * 低代码关联页配置实体。
 *
 * <p>存储关联页设计器产出的 JSON 配置，定义实体间的关联关系和关联页面引用，
 * 用于在详情页中动态渲染关联数据展示区域。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_related_page")
public class LowCodeRelatedPage extends BaseEntity {

    /** 关联页编码（唯一标识） */
    @NotBlank(message = "关联页编码不能为空")
    @Size(max = 64, message = "关联页编码长度不能超过 64 个字符")
    private String code;

    /** 关联页名称 */
    @NotBlank(message = "关联页名称不能为空")
    @Size(max = 128, message = "关联页名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 关联页配置 JSON（关联关系 + 关联页面引用） */
    @NotBlank(message = "关联页配置不能为空")
    private String relatedConfig;

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
