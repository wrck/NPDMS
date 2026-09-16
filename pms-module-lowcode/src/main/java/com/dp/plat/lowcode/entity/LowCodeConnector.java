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
 * 低代码连接器实体。
 *
 * <p>支持两种类型：REST（HTTP 调用）/ DB（数据库查询）。配置以 JSON 字符串存储。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_connector")
public class LowCodeConnector extends BaseEntity {

    /** 连接器编码（唯一） */
    @NotBlank(message = "连接器编码不能为空")
    @Size(max = 64, message = "连接器编码长度不能超过 64 个字符")
    private String code;

    /** 连接器名称 */
    @NotBlank(message = "连接器名称不能为空")
    @Size(max = 128, message = "连接器名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 类型: REST / DB */
    @NotBlank(message = "连接器类型不能为空")
    @Size(max = 16, message = "连接器类型长度不能超过 16 个字符")
    private String type;

    /** 配置 JSON */
    @NotBlank(message = "配置不能为空")
    private String config;

    /** 状态: ACTIVE / INACTIVE */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "ACTIVE";

    /** 版本号（乐观锁） */
    @Version
    @Builder.Default
    private Integer version = 1;

    /** 业务类型 */
    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;
}
