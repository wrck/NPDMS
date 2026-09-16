package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 晋升管道状态 DTO（批次5-T2，借鉴 OutSystems LifeTime 管道图）。
 *
 * <p>表示一个配置编码在 DEV/TEST/PROD 三环境下的最新版本与门禁状态，
 * 用于前端绘制管道图与显示门禁检查结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionPipelineDTO {
    /** 配置编码 */
    private String configCode;
    /** 配置类型 */
    private String configType;
    /** DEV 环境最新版本（可能为 null） */
    private VersionBrief devVersion;
    /** TEST 环境最新版本 */
    private VersionBrief testVersion;
    /** PROD 环境最新版本 */
    private VersionBrief prodVersion;
    /** DEV→TEST 门禁检查结果 */
    private GateBrief devToTestGate;
    /** TEST→PROD 门禁检查结果 */
    private GateBrief testToProdGate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionBrief {
        private Integer version;
        private String status;
        private String changeLog;
        private String createBy;
        private String createTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateBrief {
        /** 是否通过 */
        private boolean passed;
        /** 失败规则数（0 表示通过） */
        private int failureCount;
        /** 失败规则摘要（前 3 条） */
        @Builder.Default
        private List<String> failureSummaries = new ArrayList<>();
    }
}
