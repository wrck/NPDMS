package com.dp.plat.lowcode.engine.connector;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器。
 *
 * <p>管理 DB 连接器对应的 HikariDataSource 实例，按 code 缓存。
 * 支持 register / get / unregister 三个操作。</p>
 */
@Slf4j
@Component
public class DynamicDataSourceManager {

    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplateMap = new ConcurrentHashMap<>();

    public JdbcTemplate register(String code, String url, String username, String password, String driverClassName) {
        return jdbcTemplateMap.computeIfAbsent(code, k -> {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName(driverClassName);
            ds.setMaximumPoolSize(5);
            ds.setPoolName("lowcode-db-" + code);
            dataSourceMap.put(code, ds);
            log.info("注册动态数据源: code={}, url={}", code, url);
            return new JdbcTemplate(ds);
        });
    }

    public JdbcTemplate get(String code) {
        return jdbcTemplateMap.get(code);
    }

    /**
     * 获取已注册的 DataSource（批次3-T7）。
     *
     * <p>用于需要直接获取 JDBC Connection 的场景（如多数据源统一建模）。</p>
     *
     * @param code 数据源编码
     * @return DataSource 实例，未注册返回 null
     */
    public DataSource getDataSource(String code) {
        return dataSourceMap.get(code);
    }

    public void unregister(String code) {
        DataSource ds = dataSourceMap.remove(code);
        jdbcTemplateMap.remove(code);
        if (ds instanceof HikariDataSource hikari) {
            hikari.close();
            log.info("注销动态数据源: code={}", code);
        }
    }
}
