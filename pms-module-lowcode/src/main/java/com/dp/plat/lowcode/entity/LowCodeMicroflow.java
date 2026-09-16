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
 * 低代码微流实体。
 *
 * <p>存储微流设计器产出的节点 + 边定义 JSON，支持状态流转与版本管理。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_microflow")
public class LowCodeMicroflow extends BaseEntity {

    /** 微流编码（唯一） */
    @NotBlank(message = "微流编码不能为空")
    @Size(max = 64, message = "微流编码长度不能超过 64 个字符")
    private String code;

    /** 微流名称 */
    @NotBlank(message = "微流名称不能为空")
    @Size(max = 128, message = "微流名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 微流定义 JSON（节点 + 边） */
    private String definition;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "DRAFT";

    /** 版本号（乐观锁） */
    @Version
    @Builder.Default
    private Integer version = 1;

    /** 业务类型 */
    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;
}
