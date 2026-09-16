package com.dp.plat.lowcode.version;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.dto.ConfigPackageDTO;
import com.dp.plat.lowcode.dto.DependencyValidationResult;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 晋升门禁服务（批次5-T2，借鉴 OutSystems LifeTime 环境晋升门禁）。
 *
 * <p>在 DEV→TEST→PROD 晋升前执行一系列门禁规则校验，所有规则通过才允许晋升。
 * 任一规则失败会返回详细的失败原因列表，便于前端展示。</p>
 *
 * <p>门禁规则：
 * <ul>
 *   <li>依赖完整性：晋升的配置引用的 ENTITY/FORM/LIST/CONNECTOR/RULE/MICROFLOW 必须在目标环境存在或包内自带</li>
 *   <li>版本递增：目标环境最新版本号不能高于源环境（避免回退式晋升）</li>
 *   <li>状态校验：源环境最新版本必须是 ACTIVE</li>
 *   <li>环境顺序：DEV→TEST→PROD 不能跨级（TEST 无版本时不能直接 DEV→PROD）</li>
 * </ul></p>
 *
 * <p>注：与 {@link EnvironmentPromotionService} 存在相互引用（本服务调用其依赖完整性校验，
 * 其调用本服务的门禁检查构建管道状态），故对 EnvironmentPromotionService 采用 {@code @Lazy}
 * 注入打破循环依赖，避免 Spring Boot 默认禁用循环引用时启动失败。</p>
 */
@Slf4j
@Service
public class PromotionGateService {

    private final LowCodeConfigVersionService configVersionService;
    private final EnvironmentPromotionService promotionService;

    @Autowired
    public PromotionGateService(LowCodeConfigVersionService configVersionService,
                                 @Lazy EnvironmentPromotionService promotionService) {
        this.configVersionService = configVersionService;
        this.promotionService = promotionService;
    }

    /**
     * 门禁检查结果。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GateResult {
        /** 是否通过所有门禁 */
        private boolean passed;
        /** 失败的规则列表（每条含规则名 + 失败原因） */
        @lombok.Builder.Default
        private List<GateFailure> failures = new ArrayList<>();
        /** 源环境 */
        private String sourceEnvironment;
        /** 目标环境 */
        private String targetEnvironment;
        /** 受检配置编码列表 */
        private List<String> configCodes;

        public static GateResult ok(String source, String target, List<String> codes) {
            return GateResult.builder()
                    .passed(true)
                    .sourceEnvironment(source)
                    .targetEnvironment(target)
                    .configCodes(codes)
                    .build();
        }
    }

    /**
     * 单条门禁失败记录。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GateFailure {
        /** 规则名 */
        private String rule;
        /** 失败原因 */
        private String reason;
        /** 关联的配置编码（可选） */
        private String configCode;
    }

    /**
     * 执行晋升门禁检查（不实际晋升）。
     *
     * @param sourceEnvironment 源环境（DEV/TEST）
     * @param targetEnvironment 目标环境（TEST/PROD）
     * @param configCodes       要晋升的配置编码列表
     * @return 门禁检查结果
     */
    public GateResult check(String sourceEnvironment, String targetEnvironment, List<String> configCodes) {
        List<GateFailure> failures = new ArrayList<>();

        // 规则 1：环境顺序校验（不能跨级）
        if (!isValidPromotionPath(sourceEnvironment, targetEnvironment)) {
            failures.add(GateFailure.builder()
                    .rule("ENVIRONMENT_ORDER")
                    .reason(String.format("不允许的晋升路径 %s→%s，必须按 DEV→TEST→PROD 顺序", sourceEnvironment, targetEnvironment))
                    .build());
            return GateResult.builder()
                    .passed(false)
                    .failures(failures)
                    .sourceEnvironment(sourceEnvironment)
                    .targetEnvironment(targetEnvironment)
                    .configCodes(configCodes)
                    .build();
        }

        // 规则 2：源环境必须有版本
        for (String code : configCodes) {
            LowCodeConfigVersion sourceLatest = findLatestActive(sourceEnvironment, code);
            if (sourceLatest == null) {
                failures.add(GateFailure.builder()
                        .rule("SOURCE_VERSION_EXISTS")
                        .reason(String.format("源环境 %s 中配置 '%s' 无 ACTIVE 版本", sourceEnvironment, code))
                        .configCode(code)
                        .build());
            }
        }
        if (!failures.isEmpty()) {
            return buildResult(false, failures, sourceEnvironment, targetEnvironment, configCodes);
        }

        // 规则 3：依赖完整性校验
        ConfigPackageDTO pkg = configVersionService.exportPackage(sourceEnvironment, configCodes);
        DependencyValidationResult depResult = promotionService.validatePackageDependencies(pkg);
        if (!depResult.isValid()) {
            for (DependencyValidationResult.MissingDependency md : depResult.getMissing()) {
                failures.add(GateFailure.builder()
                        .rule("DEPENDENCY_COMPLETENESS")
                        .reason(String.format("配置 '%s' 引用 %s '%s' 不存在", md.getReferencedBy(), md.getType(), md.getCode()))
                        .configCode(md.getReferencedBy())
                        .build());
            }
        }

        // 规则 4：版本递增校验（目标环境版本号不能高于源环境）
        for (String code : configCodes) {
            LowCodeConfigVersion sourceLatest = findLatestActive(sourceEnvironment, code);
            LowCodeConfigVersion targetLatest = findLatestActive(targetEnvironment, code);
            if (sourceLatest != null && targetLatest != null
                    && targetLatest.getVersion() > sourceLatest.getVersion()) {
                failures.add(GateFailure.builder()
                        .rule("VERSION_MONOTONIC")
                        .reason(String.format("配置 '%s' 目标环境 %s 版本 v%d 高于源环境 %s 版本 v%d，禁止回退式晋升",
                                code, targetEnvironment, targetLatest.getVersion(),
                                sourceEnvironment, sourceLatest.getVersion()))
                        .configCode(code)
                        .build());
            }
        }

        return buildResult(failures.isEmpty(), failures, sourceEnvironment, targetEnvironment, configCodes);
    }

    /**
     * 校验晋升路径是否合法（DEV→TEST 或 TEST→PROD，不允许跨级或反向）。
     */
    private boolean isValidPromotionPath(String source, String target) {
        return ("DEV".equals(source) && "TEST".equals(target))
                || ("TEST".equals(source) && "PROD".equals(target));
    }

    /**
     * 查询指定环境 + 配置编码下的最新 ACTIVE 版本。
     */
    private LowCodeConfigVersion findLatestActive(String environment, String configCode) {
        List<LowCodeConfigVersion> list = configVersionService.list(
                new LambdaQueryWrapper<LowCodeConfigVersion>()
                        .eq(LowCodeConfigVersion::getEnvironment, environment)
                        .eq(LowCodeConfigVersion::getConfigCode, configCode)
                        .eq(LowCodeConfigVersion::getStatus, "ACTIVE")
                        .orderByDesc(LowCodeConfigVersion::getVersion)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private GateResult buildResult(boolean passed, List<GateFailure> failures,
                                    String source, String target, List<String> codes) {
        return GateResult.builder()
                .passed(passed)
                .failures(failures)
                .sourceEnvironment(source)
                .targetEnvironment(target)
                .configCodes(codes)
                .build();
    }
}
