package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低代码组件元数据实体。
 *
 * <p>记录预置/自定义组件的注册信息与属性 JSON Schema。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_component_meta")
public class LowCodeComponentMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组件名（注册 key） */
    private String name;

    /** 显示名 */
    private String displayName;

    /** 分类: SELECTOR / INPUT / DISPLAY / ... */
    private String category;

    /** 图标 */
    private String icon;

    /** 属性 JSON Schema */
    private String propsSchema;

    /** 描述 */
    private String description;

    /** 组件版本（批次4-T8） */
    private String version;

    /** 作者（批次4-T8） */
    private String author;

    /** 状态: PUBLISHED / DRAFT / ARCHIVED（批次4-T8） */
    private String status;

    /** 标签（逗号分隔，批次4-T8） */
    private String tags;

    /** 下载量（批次4-T8） */
    private Integer downloadCount;

    /** 来源: BUILTIN / CUSTOM / MARKETPLACE（批次4-T8） */
    private String sourceType;

    /** 远程组件入口 URL（MARKETPLACE 类型，批次4-T8） */
    private String entryUrl;

    /** 是否内置组件 */
    private Integer builtin;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
