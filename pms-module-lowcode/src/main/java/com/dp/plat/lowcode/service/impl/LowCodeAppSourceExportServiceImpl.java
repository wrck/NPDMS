package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.dp.plat.common.util.SecurityUtils;
import com.dp.plat.lowcode.dto.AppSourceManifest;
import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.engine.DdlGenerator;
import com.dp.plat.lowcode.engine.DdlGeneratorFactory;
import com.dp.plat.lowcode.entity.LowCodeConnector;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.entity.LowCodeList;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;
import com.dp.plat.lowcode.entity.LowCodeRelatedPage;
import com.dp.plat.lowcode.entity.LowCodeRule;
import com.dp.plat.lowcode.entity.LowCodeTab;
import com.dp.plat.lowcode.engine.trigger.LowCodeTrigger;
import com.dp.plat.lowcode.mapper.LowCodeConnectorMapper;
import com.dp.plat.lowcode.mapper.LowCodeEntityMapper;
import com.dp.plat.lowcode.mapper.LowCodeFormMapper;
import com.dp.plat.lowcode.mapper.LowCodeListMapper;
import com.dp.plat.lowcode.mapper.LowCodeMicroflowMapper;
import com.dp.plat.lowcode.mapper.LowCodeRelatedPageMapper;
import com.dp.plat.lowcode.mapper.LowCodeRuleMapper;
import com.dp.plat.lowcode.mapper.LowCodeTabMapper;
import com.dp.plat.lowcode.mapper.LowCodeTriggerMapper;
import com.dp.plat.lowcode.service.LowCodeAppSourceExportService;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 应用源码导出服务实现（批次5-T10）。
 *
 * <p>借鉴网易轻舟源码导出 — 无黑盒引擎。将按 bizType 分组的全部低代码配置
 * 打包为结构化 ZIP，内含可读 JSON 配置、多方言 DDL 脚本、独立部署 POM 与 README。</p>
 *
 * <p><b>核心设计</b>：</p>
 * <ul>
 *   <li><b>无黑盒</b>：所有配置以可读 JSON 导出，DDL 为标准 SQL（3 方言），
 *       连接器凭据脱敏（替换为占位符），运行时依赖开源 pms-lowcode Maven 模块</li>
 *   <li><b>独立部署</b>：生成的 pom.xml 可直接 mvn package 构建，
 *       DDL 脚本按目标数据库选择执行，配置 JSON 可导入任意低代码平台实例</li>
 *   <li><b>多方言 DDL</b>：复用 {@link DdlGeneratorFactory} 为每个实体生成
 *       MySQL / PostgreSQL / SQL Server 三套 CREATE TABLE 脚本</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeAppSourceExportServiceImpl implements LowCodeAppSourceExportService {

    private final LowCodeEntityMapper entityMapper;
    private final LowCodeFormMapper formMapper;
    private final LowCodeListMapper listMapper;
    private final LowCodeTabMapper tabMapper;
    private final LowCodeRelatedPageMapper relatedPageMapper;
    private final LowCodeMicroflowMapper microflowMapper;
    private final LowCodeRuleMapper ruleMapper;
    private final LowCodeConnectorMapper connectorMapper;
    private final LowCodeTriggerMapper triggerMapper;
    private final LowCodeEntityService entityService;
    private final DdlGeneratorFactory ddlGeneratorFactory;
    private final ObjectMapper objectMapper;

    /** 连接器配置中需要脱敏的 JSON 字段名 */
    private static final String[] SENSITIVE_FIELDS = {
            "password", "token", "apiKey", "secret", "clientSecret", "accessToken", "refreshToken"
    };

    /** 脱敏占位符 */
    private static final String CREDENTIAL_PLACEHOLDER = "${CONNECTOR_CREDENTIAL_PLACEHOLDER}";

    @Override
    public AppSourceManifest previewManifest(String bizType) {
        Map<String, Integer> counts = countConfigs(bizType);
        List<String> tables = listEntityTables(bizType);
        return AppSourceManifest.builder()
                .appCode(bizType == null ? "ALL" : bizType)
                .appName(bizType == null ? "全部应用" : bizType)
                .exportTime(LocalDateTime.now())
                .exportBy(SecurityUtils.getCurrentUsername())
                .platformVersion("1.0.0-SNAPSHOT")
                .configCounts(counts)
                .entityTables(tables)
                .build();
    }

    @Override
    public byte[] exportAsZip(String bizType) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. 收集所有配置（按 bizType 过滤，null 时查全部）
            List<LowCodeEntity> entities = entityMapper.selectList(bizTypeWrapper(LowCodeEntity::getBizType, bizType));
            List<LowCodeForm> forms = formMapper.selectList(bizTypeWrapper(LowCodeForm::getBizType, bizType));
            List<LowCodeList> lists = listMapper.selectList(bizTypeWrapper(LowCodeList::getBizType, bizType));
            List<LowCodeTab> tabs = tabMapper.selectList(bizTypeWrapper(LowCodeTab::getBizType, bizType));
            List<LowCodeRelatedPage> relatedPages = relatedPageMapper.selectList(bizTypeWrapper(LowCodeRelatedPage::getBizType, bizType));
            List<LowCodeMicroflow> microflows = microflowMapper.selectList(bizTypeWrapper(LowCodeMicroflow::getBizType, bizType));
            List<LowCodeRule> rules = ruleMapper.selectList(bizTypeWrapper(LowCodeRule::getBizType, bizType));
            List<LowCodeConnector> connectors = connectorMapper.selectList(bizTypeWrapper(LowCodeConnector::getBizType, bizType));

            // 触发器没有 bizType 字段，按已导出的微流 code 过滤
            List<String> microflowCodes = microflows.stream()
                    .map(LowCodeMicroflow::getCode)
                    .collect(Collectors.toList());
            List<LowCodeTrigger> triggers = listTriggersByMicroflowCodes(microflowCodes);

            // 2. 写入各目录 JSON
            writeEntities(zos, entities);
            writeForms(zos, forms);
            writeLists(zos, lists);
            writeTabs(zos, tabs);
            writeRelatedPages(zos, relatedPages);
            writeMicroflows(zos, microflows);
            writeRules(zos, rules);
            writeConnectors(zos, connectors);
            writeTriggers(zos, triggers);

            // 3. 写入多方言 DDL
            writeDdlScripts(zos, entities);

            // 4. 写入 POM + README
            writePomXml(zos, bizType);
            writeReadme(zos, bizType, entities, forms, lists, microflows, rules, connectors, triggers, tabs, relatedPages);

            // 5. 写入 manifest
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("ENTITY", entities.size());
            counts.put("FORM", forms.size());
            counts.put("LIST", lists.size());
            counts.put("TAB", tabs.size());
            counts.put("RELATED_PAGE", relatedPages.size());
            counts.put("MICROFLOW", microflows.size());
            counts.put("RULE", rules.size());
            counts.put("CONNECTOR", connectors.size());
            counts.put("TRIGGER", triggers.size());

            AppSourceManifest manifest = AppSourceManifest.builder()
                    .appCode(bizType == null ? "ALL" : bizType)
                    .appName(bizType == null ? "全部应用" : bizType)
                    .exportTime(LocalDateTime.now())
                    .exportBy(SecurityUtils.getCurrentUsername())
                    .platformVersion("1.0.0-SNAPSHOT")
                    .configCounts(counts)
                    .entityTables(entities.stream().map(LowCodeEntity::getTableName).collect(Collectors.toList()))
                    .build();
            addZipEntry(zos, "app-manifest.json", toJsonBytes(manifest));

            zos.finish();
            zos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("应用源码导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listExportableApps() {
        // 查询所有非 null 的 bizType 去重
        List<LowCodeEntity> entities = entityMapper.selectList(
                new LambdaQueryWrapper<LowCodeEntity>().isNotNull(LowCodeEntity::getBizType));
        return entities.stream()
                .map(LowCodeEntity::getBizType)
                .filter(b -> b != null && !b.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 内部方法：写入各类配置
    // =========================================================================

    private void writeEntities(ZipOutputStream zos, List<LowCodeEntity> entities) throws IOException {
        for (LowCodeEntity entity : entities) {
            EntityDesignDTO design = entityService.getDesign(entity.getId());
            addZipEntry(zos, "entities/" + entity.getCode() + ".json", toJsonBytes(design));
        }
    }

    private void writeForms(ZipOutputStream zos, List<LowCodeForm> forms) throws IOException {
        for (LowCodeForm form : forms) {
            addZipEntry(zos, "forms/" + form.getCode() + ".json", toJsonBytes(form));
        }
    }

    private void writeLists(ZipOutputStream zos, List<LowCodeList> lists) throws IOException {
        for (LowCodeList list : lists) {
            addZipEntry(zos, "lists/" + list.getCode() + ".json", toJsonBytes(list));
        }
    }

    private void writeTabs(ZipOutputStream zos, List<LowCodeTab> tabs) throws IOException {
        for (LowCodeTab tab : tabs) {
            addZipEntry(zos, "tabs/" + tab.getCode() + ".json", toJsonBytes(tab));
        }
    }

    private void writeRelatedPages(ZipOutputStream zos, List<LowCodeRelatedPage> pages) throws IOException {
        for (LowCodeRelatedPage page : pages) {
            addZipEntry(zos, "related-pages/" + page.getCode() + ".json", toJsonBytes(page));
        }
    }

    private void writeMicroflows(ZipOutputStream zos, List<LowCodeMicroflow> microflows) throws IOException {
        for (LowCodeMicroflow microflow : microflows) {
            addZipEntry(zos, "microflows/" + microflow.getCode() + ".json", toJsonBytes(microflow));
        }
    }

    private void writeRules(ZipOutputStream zos, List<LowCodeRule> rules) throws IOException {
        for (LowCodeRule rule : rules) {
            addZipEntry(zos, "rules/" + rule.getCode() + ".json", toJsonBytes(rule));
        }
    }

    private void writeConnectors(ZipOutputStream zos, List<LowCodeConnector> connectors) throws IOException {
        for (LowCodeConnector connector : connectors) {
            // 脱敏处理：将敏感字段值替换为占位符
            String safeConfig = redactCredentials(connector.getConfig());
            Map<String, Object> safeConnector = new LinkedHashMap<>();
            safeConnector.put("id", connector.getId());
            safeConnector.put("code", connector.getCode());
            safeConnector.put("name", connector.getName());
            safeConnector.put("type", connector.getType());
            safeConnector.put("description", connector.getDescription());
            safeConnector.put("config", safeConfig);
            safeConnector.put("bizType", connector.getBizType());
            safeConnector.put("version", connector.getVersion());
            safeConnector.put("_note", "凭据已脱敏，部署时需手动填入真实值");
            addZipEntry(zos, "connectors/" + connector.getCode() + ".json", toJsonBytes(safeConnector));
        }
    }

    private void writeTriggers(ZipOutputStream zos, List<LowCodeTrigger> triggers) throws IOException {
        for (LowCodeTrigger trigger : triggers) {
            addZipEntry(zos, "triggers/" + trigger.getCode() + ".json", toJsonBytes(trigger));
        }
    }

    // =========================================================================
    // DDL 脚本生成
    // =========================================================================

    private void writeDdlScripts(ZipOutputStream zos, List<LowCodeEntity> entities) throws IOException {
        if (entities.isEmpty()) return;

        // 构建 entityId → tableName 映射（用于外键目标表推导）
        Map<Long, String> entityIdToTableName = entities.stream()
                .collect(Collectors.toMap(LowCodeEntity::getId, LowCodeEntity::getTableName, (a, b) -> a));

        // 为三种方言各生成一份 DDL 脚本
        for (String dialect : List.of("mysql", "postgresql", "sqlserver")) {
            DdlGenerator generator = ddlGeneratorFactory.resolve(dialect);
            StringBuilder sb = new StringBuilder();
            sb.append("-- ").append(dialect.toUpperCase()).append(" DDL 脚本\n");
            sb.append("-- 生成时间: ").append(LocalDateTime.now()).append("\n");
            sb.append("-- 实体数量: ").append(entities.size()).append("\n\n");

            for (LowCodeEntity entity : entities) {
                EntityDesignDTO design = entityService.getDesign(entity.getId());
                try {
                    String ddl = generator.generateCreateTable(
                            design.getEntity(), design.getFields(), design.getRelations(), entityIdToTableName);
                    sb.append("-- 实体: ").append(entity.getCode())
                            .append(" (").append(entity.getTableName()).append(")\n");
                    sb.append(ddl).append("\n\n");
                } catch (Exception e) {
                    sb.append("-- [ERROR] 实体 ").append(entity.getCode())
                            .append(" DDL 生成失败: ").append(e.getMessage()).append("\n\n");
                    log.warn("DDL 生成失败: entity={}, dialect={}", entity.getCode(), dialect, e);
                }
            }
            addZipEntry(zos, "ddl/" + dialect + ".sql", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // POM + README 生成
    // =========================================================================

    private void writePomXml(ZipOutputStream zos, String bizType) throws IOException {
        String artifactId = "lowcode-app" + (bizType == null ? "" : "-" + bizType.toLowerCase());
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.2.5</version>
                        <relativePath/>
                    </parent>

                    <groupId>com.dp.plat</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>
                    <name>LowCode Application - %s</name>

                    <description>
                        独立部署的低代码应用（无黑盒引擎）。
                        配置 JSON 在 entities/forms/lists/... 目录，DDL 脚本在 ddl/ 目录。
                        运行时依赖开源的 pms-lowcode 模块，无任何黑盒引擎。
                    </description>

                    <properties>
                        <java.version>17</java.version>
                    </properties>

                    <dependencies>
                        <!-- 低代码运行时（开源，非黑盒） -->
                        <dependency>
                            <groupId>com.dp.plat</groupId>
                            <artifactId>pms-lowcode</artifactId>
                            <version>1.0.0-SNAPSHOT</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.baomidou</groupId>
                            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>mysql</groupId>
                            <artifactId>mysql-connector-java</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId, bizType == null ? "ALL" : bizType);
        addZipEntry(zos, "pom.xml", pom.getBytes(StandardCharsets.UTF_8));
    }

    private void writeReadme(ZipOutputStream zos, String bizType,
                              List<LowCodeEntity> entities, List<LowCodeForm> forms,
                              List<LowCodeList> lists, List<LowCodeMicroflow> microflows,
                              List<LowCodeRule> rules, List<LowCodeConnector> connectors,
                              List<LowCodeTrigger> triggers, List<LowCodeTab> tabs,
                              List<LowCodeRelatedPage> relatedPages) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 低代码应用源码 — ").append(bizType == null ? "全部" : bizType).append("\n\n");
        sb.append("## 概述\n\n");
        sb.append("本包为低代码平台导出的应用源码，**无黑盒引擎**。\n");
        sb.append("所有配置均为可读 JSON，DDL 为标准 SQL，运行时依赖开源的 `pms-lowcode` Maven 模块。\n\n");
        sb.append("## 目录结构\n\n");
        sb.append("| 目录 | 说明 | 数量 |\n");
        sb.append("|------|------|------|\n");
        sb.append("| entities/ | 实体设计（含字段+关联） | ").append(entities.size()).append(" |\n");
        sb.append("| forms/ | 表单配置 | ").append(forms.size()).append(" |\n");
        sb.append("| lists/ | 列表配置 | ").append(lists.size()).append(" |\n");
        sb.append("| tabs/ | 标签页配置 | ").append(tabs.size()).append(" |\n");
        sb.append("| related-pages/ | 关联页配置 | ").append(relatedPages.size()).append(" |\n");
        sb.append("| microflows/ | 微流定义 | ").append(microflows.size()).append(" |\n");
        sb.append("| rules/ | 规则定义 | ").append(rules.size()).append(" |\n");
        sb.append("| connectors/ | 连接器配置（凭据已脱敏） | ").append(connectors.size()).append(" |\n");
        sb.append("| triggers/ | 触发器定义 | ").append(triggers.size()).append(" |\n");
        sb.append("| ddl/ | 多方言 DDL 脚本（mysql/postgresql/sqlserver） | 3 |\n\n");
        sb.append("## 独立部署步骤\n\n");
        sb.append("1. **执行 DDL**：根据目标数据库选择 `ddl/{dialect}.sql` 执行\n");
        sb.append("2. **导入配置**：将各目录 JSON 导入目标低代码平台实例（通过 importConfig API）\n");
        sb.append("3. **配置连接器**：编辑 `connectors/*.json`，将 `${CONNECTOR_CREDENTIAL_PLACEHOLDER}` 替换为真实凭据\n");
        sb.append("4. **构建部署**：`mvn package` 生成可执行 JAR，`java -jar` 启动\n\n");
        sb.append("## 无黑盒引擎说明\n\n");
        sb.append("- 配置 JSON 完全可读，无加密/混淆\n");
        sb.append("- DDL 为标准 SQL，可直接在数据库执行\n");
        sb.append("- 运行时 `pms-lowcode` 为开源 Maven 依赖，源码可审查\n");
        sb.append("- 连接器凭据已脱敏，需手动填入（安全考虑）\n");
        sb.append("\n---\n导出时间: ").append(LocalDateTime.now()).append("\n");
        addZipEntry(zos, "README.md", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /**
     * 构建 bizType 过条件的 LambdaQueryWrapper。
     * bizType 为 null/空时返回空 wrapper（查全部）。
     */
    private <T> LambdaQueryWrapper<T> bizTypeWrapper(
            SFunction<T, ?> bizTypeGetter, String bizType) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(bizTypeGetter, bizType);
        }
        return wrapper;
    }

    /**
     * 查询触发器：按 targetCode 匹配已导出的微流 code（触发器无 bizType 字段）。
     */
    private List<LowCodeTrigger> listTriggersByMicroflowCodes(List<String> microflowCodes) {
        if (microflowCodes.isEmpty()) return List.of();
        return triggerMapper.selectList(
                new LambdaQueryWrapper<LowCodeTrigger>()
                        .eq(LowCodeTrigger::getTargetType, "MICROFLOW")
                        .in(LowCodeTrigger::getTargetCode, microflowCodes));
    }

    private Map<String, Integer> countConfigs(String bizType) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ENTITY", entityMapper.selectList(bizTypeWrapper(LowCodeEntity::getBizType, bizType)).size());
        counts.put("FORM", formMapper.selectList(bizTypeWrapper(LowCodeForm::getBizType, bizType)).size());
        counts.put("LIST", listMapper.selectList(bizTypeWrapper(LowCodeList::getBizType, bizType)).size());
        counts.put("TAB", tabMapper.selectList(bizTypeWrapper(LowCodeTab::getBizType, bizType)).size());
        counts.put("RELATED_PAGE", relatedPageMapper.selectList(bizTypeWrapper(LowCodeRelatedPage::getBizType, bizType)).size());
        counts.put("MICROFLOW", microflowMapper.selectList(bizTypeWrapper(LowCodeMicroflow::getBizType, bizType)).size());
        counts.put("RULE", ruleMapper.selectList(bizTypeWrapper(LowCodeRule::getBizType, bizType)).size());
        counts.put("CONNECTOR", connectorMapper.selectList(bizTypeWrapper(LowCodeConnector::getBizType, bizType)).size());
        return counts;
    }

    private List<String> listEntityTables(String bizType) {
        return entityMapper.selectList(bizTypeWrapper(LowCodeEntity::getBizType, bizType))
                .stream()
                .map(LowCodeEntity::getTableName)
                .collect(Collectors.toList());
    }

    /**
     * 脱敏连接器配置 JSON 中的敏感字段。
     * <p>简单字符串替换：匹配 "fieldName":"value" 模式，将 value 替换为占位符。
     * 这不是完美的 JSON 操作，但对常见凭据字段名有效且不引入额外依赖。</p>
     */
    private String redactCredentials(String configJson) {
        if (configJson == null || configJson.isBlank()) return configJson;
        String result = configJson;
        for (String field : SENSITIVE_FIELDS) {
            // 匹配 "field":"any_value" 或 "field": "any_value"（含各种引号/空格组合）
            result = result.replaceAll(
                    "(\"(?i:" + field + ")\"\\s*:\\s*)\"[^\"]*\"",
                    "$1\"" + CREDENTIAL_PLACEHOLDER + "\"");
        }
        return result;
    }

    private void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private byte[] toJsonBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.warn("序列化失败: {}", e.getMessage());
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
