package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 依赖校验结果。
 *
 * <p>记录配置包导入前的依赖完整性校验输出：缺失依赖清单 + 是否通过校验。
 * 借鉴 Appsmith 的依赖校验模型，用于在环境晋升时提前发现引用断裂。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependencyValidationResult {

    /** 是否通过校验（缺失依赖清单为空时为 true） */
    private boolean valid;

    /** 缺失依赖清单 */
    @Builder.Default
    private List<MissingDependency> missing = new ArrayList<>();

    /**
     * 缺失依赖描述。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingDependency {
        /** 依赖配置类型: ENTITY/FORM/LIST/MICROFLOW/CONNECTOR/RULE/PROCESS */
        private String type;
        /** 依赖配置编码 */
        private String code;
        /** 引用方描述，如 "FORM 'formOrder'" */
        private String referencedBy;
    }
}
