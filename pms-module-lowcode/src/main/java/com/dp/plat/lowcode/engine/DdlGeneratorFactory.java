package com.dp.plat.lowcode.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DDL 方言工厂（批次3-T10）。
 *
 * <p>按数据库类型（{@code dbType}，与 {@code LowCodeDataSource.dbType} 对齐）
 * 解析对应的 {@link DdlGenerator} 实现。</p>
 *
 * <p>解析规则：
 * <ul>
 *   <li>dbType=mysql → {@link MySQLDdlGenerator}</li>
 *   <li>dbType=postgresql → {@link PostgreSQLDdlGenerator}</li>
 *   <li>dbType=sqlserver → {@link SqlServerDdlGenerator}</li>
 *   <li>dbType 为 null/未知 → 回退到 MySQL（默认方言，由 @Primary 标记）</li>
 * </ul></p>
 *
 * <p>使用场景：多数据源模式下，根据 {@code LowCodeDataSource.dbType} 选择对应方言
 * 生成 DDL。默认数据源（主库 MySQL）仍由 {@code DdlExecutionServiceImpl} 直接注入
 * @Primary 的 MySQLDdlGenerator，无需经过本工厂。</p>
 */
@Slf4j
@Component
public class DdlGeneratorFactory {

    private final Map<String, DdlGenerator> generatorsByDialect;
    private final DdlGenerator defaultGenerator;

    /**
     * Spring 自动注入所有 {@link DdlGenerator} Bean。
     *
     * @param generators 所有方言实现（含 @Primary 的 MySQLDdlGenerator）
     */
    public DdlGeneratorFactory(List<DdlGenerator> generators) {
        this.generatorsByDialect = generators.stream()
                .collect(Collectors.toMap(DdlGenerator::getDialect, Function.identity()));
        // 默认回退到 mysql 方言
        this.defaultGenerator = this.generatorsByDialect.getOrDefault("mysql",
                generators.isEmpty() ? null : generators.get(0));
        log.info("DdlGeneratorFactory 初始化完成，已注册方言: {}", generatorsByDialect.keySet());
    }

    /**
     * 按数据库类型解析 DDL 生成器。
     *
     * @param dbType 数据库类型（mysql/postgresql/sqlserver），null 或未知回退到 MySQL
     * @return 对应的 DdlGenerator 实现
     * @throws IllegalStateException 当没有任何 DdlGenerator Bean 注册时
     */
    public DdlGenerator resolve(String dbType) {
        if (dbType == null || dbType.isBlank()) {
            return getDefault();
        }
        DdlGenerator resolved = generatorsByDialect.get(dbType.toLowerCase());
        if (resolved == null) {
            log.warn("不支持的数据库类型: {}，回退到默认方言 mysql", dbType);
            return getDefault();
        }
        return resolved;
    }

    /**
     * 返回默认方言生成器（MySQL）。
     *
     * @return 默认 DdlGenerator
     * @throws IllegalStateException 当没有任何 DdlGenerator Bean 注册时
     */
    public DdlGenerator getDefault() {
        if (defaultGenerator == null) {
            throw new IllegalStateException("未找到任何 DdlGenerator 实现，请检查 Spring 组件扫描配置");
        }
        return defaultGenerator;
    }

    /**
     * 判断是否支持指定数据库类型。
     *
     * @param dbType 数据库类型
     * @return true 表示已注册对应方言
     */
    public boolean supports(String dbType) {
        return dbType != null && generatorsByDialect.containsKey(dbType.toLowerCase());
    }
}
