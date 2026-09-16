package com.dp.plat.lowcode.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 低代码配置通用查询条件。
 *
 * <p>四类配置（表单/列表/标签页/关联页）的查询字段一致，统一使用此 DTO 承载查询参数：
 * code（精确或前缀匹配）、name（模糊匹配）、status（精确匹配）、bizType（精确匹配）。</p>
 */
@Data
public class LowCodeConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置编码（模糊匹配） */
    private String code;

    /** 配置名称（模糊匹配） */
    private String name;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    /** 业务类型 */
    private String bizType;
}
