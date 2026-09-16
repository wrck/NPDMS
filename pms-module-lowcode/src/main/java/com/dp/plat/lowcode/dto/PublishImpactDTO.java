package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布影响范围分析 DTO（批次5-T5，借鉴 OutSystems LifeTime 影响分析）。
 *
 * <p>分析某配置（如 ENTITY）发布/回滚时，会影响的下游配置列表：
 * <ul>
 *   <li>直接引用：下游配置快照中直接引用了该配置的 code</li>
 *   <li>间接引用：通过中间配置链式引用（本版本仅做一层直接引用分析）</li>
 * </ul></p>
 *
 * <p>典型场景：
 * <ul>
 *   <li>ENTITY 变更 → 影响引用它的 FORM/LIST</li>
 *   <li>CONNECTOR 变更 → 影响引用它的 MICROFLOW</li>
 *   <li>RULE 变更 → 影响引用它的 MICROFLOW</li>
 *   <li>MICROFLOW 变更 → 影响引用它的 TRIGGER/PROCESS_BINDING</li>
 * </ul></p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishImpactDTO {
    /** 源配置类型 */
    private String sourceConfigType;
    /** 源配置 ID */
    private Long sourceConfigId;
    /** 源配置编码 */
    private String sourceConfigCode;
    /** 源版本号（如回滚到 v3） */
    private Integer sourceVersion;
    /** 受影响的下游配置列表 */
    @Builder.Default
    private List<ImpactItem> impactedConfigs = new ArrayList<>();
    /** 受影响配置总数 */
    private int totalImpacted;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactItem {
        /** 下游配置类型 */
        private String configType;
        /** 下游配置 ID */
        private Long configId;
        /** 下游配置编码 */
        private String configCode;
        /** 引用字段名（如 entityCode/connectorCode） */
        private String referenceField;
        /** 下游配置当前状态（如 PUBLISHED/DRAFT） */
        private String status;
        /** 影响 severity: HIGH/MEDIUM/LOW */
        private String severity;
    }
}
