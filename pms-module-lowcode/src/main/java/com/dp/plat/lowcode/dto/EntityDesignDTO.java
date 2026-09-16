package com.dp.plat.lowcode.dto;

import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeRelation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 实体设计传输对象（含实体 + 字段 + 关联）。
 */
@Data
public class EntityDesignDTO {

    @Valid
    @NotNull(message = "实体定义不能为空")
    private LowCodeEntity entity;

    @Valid
    @NotNull(message = "字段列表不能为空")
    private List<LowCodeField> fields;

    @Valid
    private List<LowCodeRelation> relations;
}
