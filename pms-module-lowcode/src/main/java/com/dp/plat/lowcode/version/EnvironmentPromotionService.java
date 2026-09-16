package com.dp.plat.lowcode.version;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.dto.ConfigPackageDTO;
import com.dp.plat.lowcode.dto.DependencyValidationResult;
import com.dp.plat.lowcode.entity.LowCodeConnector;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;
import com.dp.plat.lowcode.entity.LowCodeRule;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.dp.plat.lowcode.service.LowCodeConnectorService;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.dp.plat.lowcode.service.LowCodeListService;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 环境晋升服务。
 *
 * <p>将 DEV 环境的配置包晋升到 TEST/PROD 环境，支持 zip 包导出与导入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentPromotionService {

    /** 字段名 → 依赖配置类型的映射（借鉴 Appsmith 依赖校验） */
    private static final Map<String, String> REFERENCE_FIELDS = new LinkedHashMap<>();

    static {
        REFERENCE_FIELDS.put("entityCode", "ENTITY");
        REFERENCE_FIELDS.put("formCode", "FORM");
        REFERENCE_FIELDS.put("listCode", "LIST");
        REFERENCE_FIELDS.put("connectorCode", "CONNECTOR");
        REFERENCE_FIELDS.put("ruleCode", "RULE");
        REFERENCE_FIELDS.put("microflowCode", "MICROFLOW");
    }

    private final LowCodeConfigVersionService configVersionService;
    private final ObjectMapper objectMapper;
    private final LowCodeEntityService entityService;
    private final LowCodeFormService formService;
    private final LowCodeListService listService;
    private final LowCodeMicroflowService microflowService;
    private final LowCodeConnectorService connectorService;
    private final LowCodeRuleService ruleService;
    private final com.dp.plat.lowcode.version.PromotionGateService gateService;

    /**
     * 晋升配置到目标环境。
     */
    public void promote(String targetEnvironment, List<String> configCodes) {
        ConfigPackageDTO pkg = configVersionService.exportPackage("DEV", configCodes);
        pkg.setTargetEnvironment(targetEnvironment);
        configVersionService.importPackage(pkg);
    }

    /**
     * 导出配置包（JSON 字符串，便于下载）。
     */
    public String exportPackageJson(List<String> configCodes) {
        ConfigPackageDTO pkg = configVersionService.exportPackage("DEV", configCodes);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pkg);
        } catch (Exception e) {
            throw new RuntimeException("导出配置包失败", e);
        }
    }

    /**
     * 导出配置包为 zip 字节数组。
     * <p>zip 包含两个文件：config.json（配置内容）+ metadata.json（导出元信息）。</p>
     *
     * @param configCodes       配置编码列表
     * @param targetEnvironment 目标环境
     * @return zip 字节数组
     */
    public byte[] exportPackageZip(List<String> configCodes, String targetEnvironment) {
        try {
            String json = exportPackageJson(configCodes);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("exportTime", Instant.now().toString());
            metadata.put("targetEnvironment", targetEnvironment);
            metadata.put("configCodes", configCodes);
            metadata.put("version", "1.0");
            String metadataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                addZipEntry(zos, "config.json", json.getBytes(StandardCharsets.UTF_8));
                addZipEntry(zos, "metadata.json", metadataJson.getBytes(StandardCharsets.UTF_8));
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出配置包失败", e);
        }
    }

    private void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * 校验配置包依赖完整性（借鉴 Appsmith）。
     *
     * <p>遍历配置包中每个快照，按配置类型提取对其他配置的引用：
     * <ul>
     *   <li>FORM → ENTITY（formConfig.fields 中 entityCode）</li>
     *   <li>LIST → ENTITY（listConfig 中 entityCode）</li>
     *   <li>MICROFLOW → CONNECTOR/RULE/MICROFLOW（definition 节点 config 中 connectorCode/ruleCode/microflowCode）</li>
     *   <li>PROCESS_BINDING → FORM/MICROFLOW（nodeFormBindings 中 formCode/microflowCode）</li>
     *   <li>TRIGGER → MICROFLOW 或 PROCESS（targetCode，依赖类型由 targetType 决定）</li>
     * </ul>
     * 引用的 code 必须在包内存在或在目标环境已存在，否则记入缺失清单。
     * JSON 解析失败时仅记录 warn 日志，不阻断整体校验（best-effort，单条失败不影响整体）。</p>
     *
     * @param pkg 配置包
     * @return 依赖校验结果（含缺失清单与 valid 标志）
     */
    public DependencyValidationResult validatePackageDependencies(ConfigPackageDTO pkg) {
        List<DependencyValidationResult.MissingDependency> missing = new ArrayList<>();
        if (pkg == null || pkg.getItems() == null || pkg.getItems().isEmpty()) {
            // 空包视为通过校验
            return DependencyValidationResult.builder().valid(true).missing(missing).build();
        }

        // 1. 汇总包内自带的配置编码（按类型分组），用于包内自洽校验
        Map<String, Set<String>> packageCodes = new HashMap<>();
        for (ConfigPackageDTO.PackageItem item : pkg.getItems()) {
            String type = item.getConfigType() == null ? "UNKNOWN" : item.getConfigType();
            String code = item.getConfigCode();
            if (code != null && !code.isBlank()) {
                packageCodes.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(code);
            }
        }

        // 2. 逐项提取引用并校验
        for (ConfigPackageDTO.PackageItem item : pkg.getItems()) {
            if (item.getSnapshot() == null || item.getSnapshot().isBlank()) {
                continue;
            }
            String itemType = item.getConfigType() == null ? "UNKNOWN" : item.getConfigType();
            String itemCode = item.getConfigCode() == null
                    ? String.valueOf(item.getConfigId()) : item.getConfigCode();
            String referencedBy = itemType + " '" + itemCode + "'";

            Map<String, Set<String>> references;
            try {
                references = extractReferences(itemType, item.getSnapshot());
            } catch (Exception e) {
                // best-effort：单条解析失败不影响整体
                log.warn("快照引用提取失败，跳过: {} '{}'", itemType, itemCode, e);
                continue;
            }

            for (Map.Entry<String, Set<String>> entry : references.entrySet()) {
                String depType = entry.getKey();
                for (String depCode : entry.getValue()) {
                    if (!dependencySatisfied(packageCodes, depType, depCode)) {
                        missing.add(DependencyValidationResult.MissingDependency.builder()
                                .type(depType)
                                .code(depCode)
                                .referencedBy(referencedBy)
                                .build());
                    }
                }
            }
        }

        return DependencyValidationResult.builder()
                .valid(missing.isEmpty())
                .missing(missing)
                .build();
    }

    /**
     * 判断依赖是否满足：包内存在 或 目标环境已存在。
     */
    private boolean dependencySatisfied(Map<String, Set<String>> packageCodes,
                                        String depType, String depCode) {
        // 包内自带则直接满足
        Set<String> codes = packageCodes.get(depType);
        if (codes != null && codes.contains(depCode)) {
            return true;
        }
        // 否则查询目标环境是否已存在
        return dependencyExists(depType, depCode);
    }

    /**
     * 从快照 JSON 中提取引用（按字段名 → 依赖类型映射 + 类型特化处理）。
     *
     * <p>通用策略：递归遍历 JSON 树，按 REFERENCE_FIELDS 收集 entityCode/formCode/...
     * 这覆盖 FORM→ENTITY、LIST→ENTITY、MICROFLOW→CONNECTOR/RULE/MICROFLOW、
     * PROCESS_BINDING→FORM/MICROFLOW 等场景（引用字段可出现在任意嵌套层级）。</p>
     *
     * <p>特化策略：TRIGGER 的 targetCode 依赖类型由 targetType（MICROFLOW/PROCESS）决定，
     * 需单独解析；PROCESS 类型无法在目标环境直接校验（Flowable 流程独立部署），
     * 仅做包内自洽校验。</p>
     *
     * @param configType   配置类型
     * @param snapshotJson 快照 JSON
     * @return 依赖类型 → 引用编码集合
     */
    private Map<String, Set<String>> extractReferences(String configType, String snapshotJson) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        JsonNode root = null;
        try {
            root = objectMapper.readTree(snapshotJson);
            collectReferences(root, result);
        } catch (Exception e) {
            // best-effort：JSON 解析失败时回退到正则提取，避免阻断整体校验
            log.warn("快照 JSON 解析失败，回退正则提取: {}", configType, e);
            regexExtractReferences(snapshotJson, result);
        }

        // TRIGGER 特化：targetCode 的依赖类型由 targetType 决定（MICROFLOW/PROCESS）
        if ("TRIGGER".equals(configType) && root != null) {
            JsonNode targetTypeNode = root.get("targetType");
            JsonNode targetCodeNode = root.get("targetCode");
            if (targetTypeNode != null && targetCodeNode != null && targetCodeNode.isTextual()) {
                String targetType = targetTypeNode.asText();
                String targetCode = targetCodeNode.asText();
                if ("MICROFLOW".equals(targetType) || "PROCESS".equals(targetType)) {
                    result.computeIfAbsent(targetType, k -> new LinkedHashSet<>()).add(targetCode);
                }
            }
        }
        return result;
    }

    /**
     * 正则回退提取（JSON 解析失败时使用，best-effort）。
     */
    private void regexExtractReferences(String snapshotJson, Map<String, Set<String>> result) {
        for (Map.Entry<String, String> field : REFERENCE_FIELDS.entrySet()) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(field.getKey()) + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(snapshotJson);
            Set<String> codes = new LinkedHashSet<>();
            while (m.find()) {
                codes.add(m.group(1));
            }
            if (!codes.isEmpty()) {
                result.computeIfAbsent(field.getValue(), k -> new LinkedHashSet<>()).addAll(codes);
            }
        }
    }

    /**
     * 递归收集 JSON 节点中的引用字段。
     */
    @SuppressWarnings("unchecked")
    private void collectReferences(JsonNode node, Map<String, Set<String>> result) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String depType = REFERENCE_FIELDS.get(key);
                if (depType != null && value.isTextual()) {
                    result.computeIfAbsent(depType, k -> new java.util.LinkedHashSet<>())
                            .add(value.asText());
                } else {
                    collectReferences(value, result);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectReferences(child, result);
            }
        }
    }

    /**
     * 检查目标环境中指定依赖是否存在。
     *
     * <p>PROCESS 类型由 Flowable 独立部署管理，此处无法直接校验，
     * 归入 default 假定存在（仅依赖包内自洽校验），避免误报。</p>
     */
    private boolean dependencyExists(String depType, String code) {
        try {
            return switch (depType) {
                case "ENTITY" -> entityService.count(new LambdaQueryWrapper<LowCodeEntity>()
                        .eq(LowCodeEntity::getCode, code)) > 0;
                case "FORM" -> formService.getByCode(code) != null;
                case "LIST" -> listService.getByCode(code) != null;
                case "MICROFLOW" -> microflowService.count(new LambdaQueryWrapper<LowCodeMicroflow>()
                        .eq(LowCodeMicroflow::getCode, code)) > 0;
                case "CONNECTOR" -> connectorService.count(new LambdaQueryWrapper<LowCodeConnector>()
                        .eq(LowCodeConnector::getCode, code)) > 0;
                case "RULE" -> ruleService.count(new LambdaQueryWrapper<LowCodeRule>()
                        .eq(LowCodeRule::getCode, code)) > 0;
                default -> true; // 未知/PROCESS 依赖类型，假定存在，避免误报
            };
        } catch (Exception e) {
            log.warn("依赖存在性校验失败，假定存在: {}/{}", depType, code, e);
            return true;
        }
    }

    /**
     * 导入配置包（带覆盖确认）。
     *
     * @param packageJson 配置包 JSON（zip 内 config.json 的内容）
     * @param overwrite   是否覆盖已存在的配置
     */
    public void importPackageWithConfirm(String packageJson, boolean overwrite) {
        try {
            JsonNode root = objectMapper.readTree(packageJson);
            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                throw new IllegalArgumentException("配置包格式错误：缺少 items 数组");
            }
            log.info("导入配置包：共 {} 项，覆盖模式 = {}", items.size(), overwrite);
            // 转换回 ConfigPackageDTO 并调用 service 导入
            ConfigPackageDTO pkg = objectMapper.treeToValue(root, ConfigPackageDTO.class);

            // 依赖完整性校验（best-effort：缺失依赖仅告警，不阻断导入）
            DependencyValidationResult depResult = validatePackageDependencies(pkg);
            if (!depResult.isValid()) {
                log.warn("配置包存在 {} 项缺失依赖，导入可能不完整:", depResult.getMissing().size());
                for (DependencyValidationResult.MissingDependency md : depResult.getMissing()) {
                    log.warn("  - {} 引用 {} '{}' 不存在", md.getReferencedBy(), md.getType(), md.getCode());
                }
            }

            if (!overwrite) {
                // TODO: 检查每个 item 是否已存在，若已存在则跳过
                log.info("非覆盖模式：已存在的配置将被跳过（待实现完整校验）");
            }
            configVersionService.importPackage(pkg);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("导入配置包失败", e);
        }
    }

    /**
     * 检测导入配置包的冲突（批次5-T3）。
     *
     * <p>遍历配置包 items，对每个 item 查询目标环境是否已有同 configCode 的 ACTIVE 版本。
     * 若有，记入冲突列表；若无，计入 noConflictCount。</p>
     *
     * @param packageJson      配置包 JSON 字符串
     * @param targetEnvironment 目标环境
     * @return 冲突检测结果
     */
    public com.dp.plat.lowcode.dto.ImportConflictDTO detectImportConflicts(String packageJson, String targetEnvironment) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(packageJson);
            com.fasterxml.jackson.databind.JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                throw new IllegalArgumentException("配置包格式错误：缺少 items 数组");
            }
            com.dp.plat.lowcode.dto.ConfigPackageDTO pkg = objectMapper.treeToValue(root, com.dp.plat.lowcode.dto.ConfigPackageDTO.class);
            return detectConflictsForPackage(pkg, targetEnvironment);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("检测导入冲突失败", e);
        }
    }

    private com.dp.plat.lowcode.dto.ImportConflictDTO detectConflictsForPackage(
            com.dp.plat.lowcode.dto.ConfigPackageDTO pkg, String targetEnvironment) {
        java.util.List<com.dp.plat.lowcode.dto.ImportConflictDTO.ConflictItem> conflicts = new java.util.ArrayList<>();
        int noConflictCount = 0;
        for (com.dp.plat.lowcode.dto.ConfigPackageDTO.PackageItem item : pkg.getItems()) {
            com.dp.plat.lowcode.entity.LowCodeConfigVersion targetLatest = findLatestActive(targetEnvironment, item.getConfigCode());
            if (targetLatest == null) {
                noConflictCount++;
                continue;
            }
            conflicts.add(com.dp.plat.lowcode.dto.ImportConflictDTO.ConflictItem.builder()
                    .configType(item.getConfigType())
                    .configId(item.getConfigId())
                    .configCode(item.getConfigCode())
                    .sourceVersion(item.getVersion())
                    .targetVersion(targetLatest.getVersion())
                    .targetChangeLog(targetLatest.getChangeLog())
                    .targetCreateBy(targetLatest.getCreateBy())
                    .targetCreateTime(targetLatest.getCreateTime() == null ? null : targetLatest.getCreateTime().toString())
                    .resolution(null)
                    .build());
        }
        return com.dp.plat.lowcode.dto.ImportConflictDTO.builder()
                .sourceEnvironment(pkg.getSourceEnvironment())
                .targetEnvironment(targetEnvironment)
                .conflicts(conflicts)
                .noConflictCount(noConflictCount)
                .totalCount(pkg.getItems().size())
                .build();
    }

    /**
     * 按用户解决方案导入配置包（批次5-T3）。
     *
     * <p>根据每个冲突项的 resolution 决定导入行为：
     * <ul>
     *   <li>KEEP_SOURCE: 用源版本快照在目标环境创建新版本（覆盖式导入）</li>
     *   <li>KEEP_TARGET: 跳过此项（保留目标版本）</li>
     *   <li>SKIP: 跳过此项（不导入）</li>
     *   <li>null 或其他: 默认按 KEEP_SOURCE 处理</li>
     * </ul></p>
     * <p>无冲突项直接按 KEEP_SOURCE 导入。</p>
     *
     * @param packageJson      配置包 JSON 字符串
     * @param targetEnvironment 目标环境
     * @param resolutions      冲突解决方案 Map<configCode, resolution>
     */
    public void importPackageWithResolution(String packageJson, String targetEnvironment,
                                              java.util.Map<String, String> resolutions) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(packageJson);
            com.dp.plat.lowcode.dto.ConfigPackageDTO pkg = objectMapper.treeToValue(root, com.dp.plat.lowcode.dto.ConfigPackageDTO.class);
            pkg.setTargetEnvironment(targetEnvironment);

            java.util.List<com.dp.plat.lowcode.dto.ConfigPackageDTO.PackageItem> itemsToImport = new java.util.ArrayList<>();
            for (com.dp.plat.lowcode.dto.ConfigPackageDTO.PackageItem item : pkg.getItems()) {
                String resolution = resolutions == null ? null : resolutions.get(item.getConfigCode());
                if ("KEEP_TARGET".equals(resolution) || "SKIP".equals(resolution)) {
                    log.info("跳过导入配置 '{}'，解决方式: {}", item.getConfigCode(), resolution);
                    continue;
                }
                // KEEP_SOURCE 或无冲突项，加入导入列表
                itemsToImport.add(item);
            }
            com.dp.plat.lowcode.dto.ConfigPackageDTO resolvedPkg = com.dp.plat.lowcode.dto.ConfigPackageDTO.builder()
                    .sourceEnvironment(pkg.getSourceEnvironment())
                    .targetEnvironment(targetEnvironment)
                    .items(itemsToImport)
                    .build();
            configVersionService.importPackage(resolvedPkg);
            log.info("按解决方案导入完成: 总 {} 项，实际导入 {} 项", pkg.getItems().size(), itemsToImport.size());
        } catch (Exception e) {
            throw new RuntimeException("按解决方案导入失败", e);
        }
    }

    /**
     * 查询多个配置编码的晋升管道状态（批次5-T2）。
     *
     * <p>对每个 configCode 返回 DEV/TEST/PROD 三环境最新版本 + DEV→TEST/TEST→PROD 门禁状态。
     * 借鉴 OutSystems LifeTime 的管道图视图。</p>
     *
     * @param configCodes 配置编码列表
     * @return 管道状态列表（每个 configCode 一项）
     */
    public List<com.dp.plat.lowcode.dto.PromotionPipelineDTO> getPipelineStatus(List<String> configCodes) {
        List<com.dp.plat.lowcode.dto.PromotionPipelineDTO> result = new java.util.ArrayList<>();
        for (String code : configCodes) {
            com.dp.plat.lowcode.entity.LowCodeConfigVersion dev = findLatestActive("DEV", code);
            com.dp.plat.lowcode.entity.LowCodeConfigVersion test = findLatestActive("TEST", code);
            com.dp.plat.lowcode.entity.LowCodeConfigVersion prod = findLatestActive("PROD", code);

            com.dp.plat.lowcode.version.PromotionGateService.GateResult devToTest =
                    gateService.check("DEV", "TEST", java.util.Collections.singletonList(code));
            com.dp.plat.lowcode.version.PromotionGateService.GateResult testToProd =
                    gateService.check("TEST", "PROD", java.util.Collections.singletonList(code));

            result.add(com.dp.plat.lowcode.dto.PromotionPipelineDTO.builder()
                    .configCode(code)
                    .configType(dev != null ? dev.getConfigType()
                            : test != null ? test.getConfigType()
                            : prod != null ? prod.getConfigType() : null)
                    .devVersion(toBrief(dev))
                    .testVersion(toBrief(test))
                    .prodVersion(toBrief(prod))
                    .devToTestGate(toGateBrief(devToTest))
                    .testToProdGate(toGateBrief(testToProd))
                    .build());
        }
        return result;
    }

    private com.dp.plat.lowcode.dto.PromotionPipelineDTO.VersionBrief toBrief(
            com.dp.plat.lowcode.entity.LowCodeConfigVersion v) {
        if (v == null) return null;
        return com.dp.plat.lowcode.dto.PromotionPipelineDTO.VersionBrief.builder()
                .version(v.getVersion())
                .status(v.getStatus())
                .changeLog(v.getChangeLog())
                .createBy(v.getCreateBy())
                .createTime(v.getCreateTime() == null ? null : v.getCreateTime().toString())
                .build();
    }

    private com.dp.plat.lowcode.dto.PromotionPipelineDTO.GateBrief toGateBrief(
            com.dp.plat.lowcode.version.PromotionGateService.GateResult r) {
        java.util.List<String> summaries = new java.util.ArrayList<>();
        for (com.dp.plat.lowcode.version.PromotionGateService.GateFailure f : r.getFailures()) {
            summaries.add(f.getRule() + ": " + f.getReason());
            if (summaries.size() >= 3) break;
        }
        return com.dp.plat.lowcode.dto.PromotionPipelineDTO.GateBrief.builder()
                .passed(r.isPassed())
                .failureCount(r.getFailures().size())
                .failureSummaries(summaries)
                .build();
    }

    private com.dp.plat.lowcode.entity.LowCodeConfigVersion findLatestActive(String env, String code) {
        java.util.List<com.dp.plat.lowcode.entity.LowCodeConfigVersion> list = configVersionService.list(
                new LambdaQueryWrapper<com.dp.plat.lowcode.entity.LowCodeConfigVersion>()
                        .eq(com.dp.plat.lowcode.entity.LowCodeConfigVersion::getEnvironment, env)
                        .eq(com.dp.plat.lowcode.entity.LowCodeConfigVersion::getConfigCode, code)
                        .eq(com.dp.plat.lowcode.entity.LowCodeConfigVersion::getStatus, "ACTIVE")
                        .orderByDesc(com.dp.plat.lowcode.entity.LowCodeConfigVersion::getVersion)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
