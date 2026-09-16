package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 低代码配置模板实体（批次5-T8 模板市场）。
 *
 * <p>用于将表单/列表/实体/微流/连接器/规则等配置以模板形式上架到市场，
 * 支持上架/下架/归档、关键词搜索、下载计数、平均评分、版本管理与参数化替换。
 * 借鉴 Zoho 模板市场 / Appsmith 模板 / Mendix App Store。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_config_template")
public class LowCodeConfigTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码（唯一，如 "project-management-form"） */
    private String code;

    /** 模板名称 */
    private String name;

    /** 配置类型: FORM / LIST / ENTITY / MICROFLOW / CONNECTOR / RULE / TAB / RELATED_PAGE */
    private String configType;

    /** 分类（如 "通用业务" "资产管理" "工作流"） */
    private String category;

    /** 完整配置 JSON 快照 */
    @TableField(value = "config_json")
    private String configJson;

    /** 缩略图 URL */
    private String thumbnail;

    /** 模板描述 */
    private String description;

    /** 作者 */
    private String author;

    /** 标签（逗号分隔） */
    private String tags;

    /** 状态: PUBLISHED / DRAFT / ARCHIVED */
    private String status;

    /** 下载量 */
    private Integer downloadCount;

    /** 评分 0-5 */
    private BigDecimal rating;

    /** 评分数 */
    private Integer ratingCount;

    /** 模板版本（如 "1.0.0"） */
    private String version;

    /** 参数化定义 JSON（如 [{"key":"entityCode","label":"实体编码","type":"string","required":true}]） */
    @TableField(value = "parameters")
    private String parameters;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
