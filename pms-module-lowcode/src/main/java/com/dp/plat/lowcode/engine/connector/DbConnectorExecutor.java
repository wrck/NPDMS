package com.dp.plat.lowcode.engine.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DB 连接器执行器。
 *
 * <p>config JSON 结构：
 * {url, username, password, driverClassName, sql, sqlType: QUERY/UPDATE, params: [...]}</p>
 *
 * <p>安全：禁止 DDL（CREATE/ALTER/DROP/TRUNCATE）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbConnectorExecutor {

    private final ObjectMapper objectMapper;
    private final DynamicDataSourceManager dynamicDataSourceManager;

    private static final Pattern DDL_PATTERN = Pattern.compile(
            "\\b(CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+TABLE|TRUNCATE|CREATE\\s+INDEX|DROP\\s+INDEX|CREATE\\s+DATABASE|DROP\\s+DATABASE)\\b",
            Pattern.CASE_INSENSITIVE);

    @SuppressWarnings("unchecked")
    public ConnectorResult execute(String configJson, Map<String, Object> params) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            String code = (String) config.getOrDefault("code", "default");
            String url = (String) config.get("url");
            String username = (String) config.get("username");
            String password = (String) config.get("password");
            String driverClassName = (String) config.getOrDefault("driverClassName", "com.mysql.cj.jdbc.Driver");
            String sql = (String) config.get("sql");
            String sqlType = (String) config.getOrDefault("sqlType", "QUERY");
            List<Object> sqlParams = (List<Object>) config.getOrDefault("params", List.of());

            if (sql == null || sql.isBlank()) {
                return ConnectorResult.error(400, "SQL 不能为空");
            }
            if (DDL_PATTERN.matcher(sql).find()) {
                return ConnectorResult.error(403, "禁止执行 DDL 语句: " + sql);
            }

            JdbcTemplate jdbcTemplate = dynamicDataSourceManager.get(code);
            if (jdbcTemplate == null) {
                jdbcTemplate = dynamicDataSourceManager.register(code, url, username, password, driverClassName);
            }

            Object[] args = sqlParams.toArray();
            if ("UPDATE".equalsIgnoreCase(sqlType)) {
                int rows = jdbcTemplate.update(sql, args);
                return ConnectorResult.ok(Map.of("affectedRows", rows));
            } else {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
                return ConnectorResult.ok(rows);
            }
        } catch (Exception e) {
            log.error("DB 连接器执行失败", e);
            return ConnectorResult.error(500, e.getMessage());
        }
    }
}
