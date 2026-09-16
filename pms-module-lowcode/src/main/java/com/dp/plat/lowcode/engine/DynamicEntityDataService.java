package com.dp.plat.lowcode.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.lowcode.dto.DynamicQueryRequest;
import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.engine.trigger.CrudTriggerExecutor;
import com.dp.plat.lowcode.engine.trigger.LowCodeTrigger;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.dp.plat.lowcode.service.LowCodeTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态实体数据访问服务。
 *
 * <p>基于 JdbcTemplate 直接操作动态生成的物理表，
 * 提供 CRUD 接口。运行时根据实体定义动态构建 SQL。</p>
 *
 * <p>CRUD 操作前后会触发类型为 CRUD 的低代码触发器（借鉴 Zoho/Budibase）：
 * BEFORE 触发器异常将阻断主操作，AFTER 触发器异常仅记录日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicEntityDataService {

    /** 合法 JOIN 类型白名单（防 SQL 注入） */
    private static final Set<String> VALID_JOIN_TYPES = Set.of("INNER", "LEFT", "RIGHT");
    /** 标识符（表名/别名/字段名）正则白名单：字母/下划线开头，最长 64 字符 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    private final LowCodeEntityService entityService;
    private final JdbcTemplate jdbcTemplate;
    private final LowCodeTriggerService triggerService;
    private final CrudTriggerExecutor crudTriggerExecutor;

    /**
     * 查询列表（分页）。
     */
    public Map<String, Object> list(String entityCode, int page, int size,
                                     Map<String, Object> filters) {
        EntityDesignDTO design = getDesignByCode(entityCode);
        String tableName = design.getEntity().getTableName();

        // 合法字段白名单（含 id），过滤条件字段名必须命中以防 SQL 注入
        Set<String> validFields = design.getFields().stream()
                .map(LowCodeField::getName)
                .collect(Collectors.toSet());
        validFields.add("id");

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filters != null) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String field = entry.getKey();
                if (!validFields.contains(field)) {
                    // 非法字段直接忽略，避免注入
                    continue;
                }
                where.append(" AND `").append(field).append("` = ?");
                params.add(entry.getValue());
            }
        }

        String countSql = "SELECT COUNT(*) FROM `" + tableName + "`" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        int offset = (page - 1) * size;
        String listSql = "SELECT * FROM `" + tableName + "`" + where
                + " LIMIT " + size + " OFFSET " + offset;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(listSql, params.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("records", records);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 高级查询：支持 LIKE/IN/BETWEEN/比较/IS NULL 等操作符、排序、OR 分组与分页。
     *
     * <p>条件按 {@code orGroup} 分组：未分组（default）条件用 AND 连接；
     * 同一命名分组内用 OR 连接，分组整体与其它条件用 AND 连接。
     * 字段名通过实体设计白名单校验以防 SQL 注入，值统一使用 {@code ?} 占位符参数化。</p>
     *
     * @param request 查询请求
     * @return MyBatis-Plus 分页对象（records/total/current/size）
     */
    public Page<Map<String, Object>> queryAdvanced(DynamicQueryRequest request) {
        EntityDesignDTO design = getDesignByCode(request.getEntityCode());
        String tableName = design.getEntity().getTableName();

        // 合法字段白名单（含 id），用于过滤条件/排序字段名以防 SQL 注入
        Set<String> validFields = design.getFields().stream()
                .map(LowCodeField::getName)
                .collect(Collectors.toSet());
        validFields.add("id");

        // 按 orGroup 分组：default 组用 AND，命名组组内 OR、组间 AND
        Map<String, List<DynamicQueryRequest.QueryCondition>> groups = new LinkedHashMap<>();
        if (request.getConditions() != null) {
            for (DynamicQueryRequest.QueryCondition c : request.getConditions()) {
                if (c.getField() == null || !validFields.contains(c.getField())) {
                    // 非法字段直接忽略，避免注入
                    continue;
                }
                String group = c.getOrGroup() != null ? c.getOrGroup() : "default";
                groups.computeIfAbsent(group, k -> new ArrayList<>()).add(c);
            }
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // default 组：AND 连接
        List<DynamicQueryRequest.QueryCondition> defaultGroup = groups.get("default");
        if (defaultGroup != null) {
            for (DynamicQueryRequest.QueryCondition c : defaultGroup) {
                where.append(" AND ");
                appendConditionFragment(where, params, c);
            }
        }

        // 命名组：组内 OR，整体 AND (...) 连接
        for (Map.Entry<String, List<DynamicQueryRequest.QueryCondition>> entry : groups.entrySet()) {
            if ("default".equals(entry.getKey())) {
                continue;
            }
            List<DynamicQueryRequest.QueryCondition> groupConditions = entry.getValue();
            if (groupConditions.isEmpty()) {
                continue;
            }
            where.append(" AND (");
            for (int i = 0; i < groupConditions.size(); i++) {
                if (i > 0) {
                    where.append(" OR ");
                }
                appendConditionFragment(where, params, groupConditions.get(i));
            }
            where.append(")");
        }

        // 排序
        StringBuilder order = new StringBuilder();
        if (request.getOrderBy() != null) {
            for (DynamicQueryRequest.OrderBy ob : request.getOrderBy()) {
                if (ob.getField() == null || !validFields.contains(ob.getField())) {
                    continue;
                }
                order.append(order.length() == 0 ? " ORDER BY" : ", ");
                order.append(" `").append(ob.getField()).append("`");
                order.append("DESC".equalsIgnoreCase(ob.getDirection()) ? " DESC" : " ASC");
            }
        }

        // 关联查询（批次3-T8：join 支持；批次6-T3：JOIN 类型/别名/ON 字段白名单校验防注入）
        StringBuilder joinClause = new StringBuilder();
        StringBuilder joinSelect = new StringBuilder();
        if (request.getJoins() != null) {
            for (DynamicQueryRequest.JoinConfig join : request.getJoins()) {
                if (join.getEntityCode() == null) continue;
                EntityDesignDTO foreignDesign = getDesignByCode(join.getEntityCode());
                if (foreignDesign == null || foreignDesign.getEntity() == null) continue;
                String foreignTable = foreignDesign.getEntity().getTableName();
                String alias = join.getAlias() != null ? join.getAlias() : join.getEntityCode();

                // 别名需命中标识符正则白名单，否则跳过该 JOIN（防注入）
                if (!IDENTIFIER_PATTERN.matcher(alias).matches()) {
                    log.warn("JOIN 别名非法，已跳过: alias={}, entityCode={}", alias, join.getEntityCode());
                    continue;
                }
                // JOIN 类型白名单校验
                String joinType = join.getJoinType() != null ? join.getJoinType().toUpperCase() : "LEFT";
                if (!VALID_JOIN_TYPES.contains(joinType)) {
                    log.warn("JOIN 类型非法，已跳过: joinType={}, entityCode={}", joinType, join.getEntityCode());
                    continue;
                }

                // 关联表合法字段白名单（在 ON 校验前构建）
                Set<String> foreignValidFields = foreignDesign.getFields().stream()
                        .map(LowCodeField::getName).collect(Collectors.toSet());
                foreignValidFields.add("id");

                // 记录 JOIN 前缀长度，便于 ON 条件全部非法时回滚（避免生成空 ON 子句导致 SQL 语法错误）
                int joinPrefixLen = joinClause.length();
                joinClause.append(" ").append(joinType).append(" JOIN `")
                        .append(foreignTable).append("` AS `").append(alias).append("` ON ");

                // ON 条件：localField 必须命中主表白名单，foreignField 必须命中关联表白名单
                List<DynamicQueryRequest.JoinOnCondition> onConds = join.getOnConditions();
                boolean onValid = false;
                if (onConds != null && !onConds.isEmpty()) {
                    for (int i = 0; i < onConds.size(); i++) {
                        DynamicQueryRequest.JoinOnCondition on = onConds.get(i);
                        if (on.getLocalField() == null || !validFields.contains(on.getLocalField())
                                || on.getForeignField() == null
                                || !foreignValidFields.contains(on.getForeignField())) {
                            // 非法 ON 条件跳过，避免注入
                            continue;
                        }
                        if (onValid) {
                            joinClause.append(" AND ");
                        }
                        joinClause.append("`").append(tableName).append("`.`")
                                .append(on.getLocalField()).append("` = `")
                                .append(alias).append("`.`")
                                .append(on.getForeignField()).append("`");
                        onValid = true;
                    }
                }
                if (!onValid) {
                    // 无合法 ON 条件：尝试默认外键约定 <alias>_id（须命中主表白名单）
                    String defaultFk = alias + "_id";
                    if (validFields.contains(defaultFk)) {
                        joinClause.append("`").append(tableName).append("`.`").append(defaultFk)
                                .append("` = `").append(alias).append("`.`id`");
                        onValid = true;
                    }
                }
                if (!onValid) {
                    // 仍无合法 ON：回滚已追加的 JOIN 前缀，跳过该 JOIN（避免空 ON 子句）
                    joinClause.setLength(joinPrefixLen);
                    log.warn("JOIN 无合法 ON 条件，已回滚跳过: entityCode={}, alias={}", join.getEntityCode(), alias);
                    continue;
                }

                // 关联表字段
                if (join.getSelectFields() != null && !join.getSelectFields().isEmpty()) {
                    for (String f : join.getSelectFields()) {
                        if (foreignValidFields.contains(f)) {
                            joinSelect.append(", `").append(alias).append("`.`").append(f)
                                    .append("` AS `").append(alias).append("_").append(f).append("`");
                        }
                    }
                } else {
                    // 默认选所有字段（加别名前缀）
                    joinSelect.append(", `").append(alias).append("`.*");
                }
            }
        }

        // 计数
        String countSql = "SELECT COUNT(*) FROM `" + tableName + "`" + joinClause + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) {
            total = 0L;
        }

        // 分页查询
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 20;
        int offset = (page - 1) * size;
        String listSql = "SELECT `" + tableName + "`.*" + joinSelect + " FROM `"
                + tableName + "`" + joinClause + where + order
                + " LIMIT " + size + " OFFSET " + offset;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(listSql, params.toArray());

        Page<Map<String, Object>> pageResult = new Page<>(page, size);
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        return pageResult;
    }

    /**
     * 构造单个条件的 SQL 片段（不含前缀 AND/OR）并填充参数。
     */
    private void appendConditionFragment(StringBuilder sb, List<Object> params,
                                          DynamicQueryRequest.QueryCondition c) {
        String col = "`" + c.getField() + "`";
        String op = c.getOperator() == null ? "EQ" : c.getOperator();
        switch (op) {
            case "EQ" -> { sb.append(col).append(" = ?"); params.add(c.getValue()); }
            case "NE" -> { sb.append(col).append(" <> ?"); params.add(c.getValue()); }
            case "LIKE" -> { sb.append(col).append(" LIKE ?"); params.add(c.getValue()); }
            case "GT" -> { sb.append(col).append(" > ?"); params.add(c.getValue()); }
            case "GE" -> { sb.append(col).append(" >= ?"); params.add(c.getValue()); }
            case "LT" -> { sb.append(col).append(" < ?"); params.add(c.getValue()); }
            case "LE" -> { sb.append(col).append(" <= ?"); params.add(c.getValue()); }
            case "IN" -> {
                Object val = c.getValue();
                if (val instanceof Collection<?> coll) {
                    if (coll.isEmpty()) {
                        // IN () 在 MySQL 非法，空集合直接置为恒假
                        sb.append("1=0");
                    } else {
                        String placeholders = coll.stream().map(v -> "?")
                                .collect(Collectors.joining(", "));
                        sb.append(col).append(" IN (").append(placeholders).append(")");
                        params.addAll(coll);
                    }
                } else {
                    // 非集合值退化为等值
                    sb.append(col).append(" = ?"); params.add(val);
                }
            }
            case "BETWEEN" -> { sb.append(col).append(" BETWEEN ? AND ?"); params.add(c.getValue()); params.add(c.getValue2()); }
            case "IS_NULL" -> sb.append(col).append(" IS NULL");
            case "IS_NOT_NULL" -> sb.append(col).append(" IS NOT NULL");
            default -> throw new IllegalArgumentException("不支持的操作符: " + op);
        }
    }

    /**
     * 查询单条记录。
     */
    public Map<String, Object> getById(String entityCode, Long id) {
        EntityDesignDTO design = getDesignByCode(entityCode);
        String sql = "SELECT * FROM `" + design.getEntity().getTableName()
                + "` WHERE id = ?";
        return jdbcTemplate.queryForMap(sql, id);
    }

    /**
     * 新增记录。
     */
    public Long create(String entityCode, Map<String, Object> data) {
        EntityDesignDTO design = getDesignByCode(entityCode);
        String tableName = design.getEntity().getTableName();

        // BEFORE_CREATE 触发器（异常则阻断）
        fireBeforeCrudTriggers(entityCode, "CREATE",
                buildContext(entityCode, "CREATE", "BEFORE", null, data));

        Map<String, String> fieldTypeMap = design.getFields().stream()
                .collect(Collectors.toMap(LowCodeField::getName, LowCodeField::getFieldType, (a, b) -> a));
        Set<String> validFields = fieldTypeMap.keySet();
        Map<String, Object> filtered = data.entrySet().stream()
                .filter(e -> validFields.contains(e.getKey()) && e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        normalizeDateValues(filtered, fieldTypeMap);

        String columns = filtered.keySet().stream()
                .map(f -> "`" + f + "`")
                .collect(Collectors.joining(", "));
        String placeholders = filtered.values().stream()
                .map(v -> "?")
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO `" + tableName + "` (" + columns + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, filtered.values().toArray());

        Long createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // AFTER_CREATE 触发器（异常不阻断）
        fireAfterCrudTriggers(entityCode, "CREATE",
                buildContext(entityCode, "CREATE", "AFTER", createdId, data));

        return createdId;
    }

    /**
     * 更新记录。
     */
    public void update(String entityCode, Long id, Map<String, Object> data) {
        EntityDesignDTO design = getDesignByCode(entityCode);
        String tableName = design.getEntity().getTableName();

        // BEFORE_UPDATE 触发器（异常则阻断）
        fireBeforeCrudTriggers(entityCode, "UPDATE",
                buildContext(entityCode, "UPDATE", "BEFORE", id, data));

        Map<String, String> fieldTypeMap = design.getFields().stream()
                .collect(Collectors.toMap(LowCodeField::getName, LowCodeField::getFieldType, (a, b) -> a));
        Set<String> validFields = fieldTypeMap.keySet().stream()
                .filter(f -> !"id".equals(f))
                .collect(Collectors.toSet());
        Map<String, Object> filtered = data.entrySet().stream()
                .filter(e -> validFields.contains(e.getKey()) && e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (filtered.isEmpty()) {
            return;
        }
        normalizeDateValues(filtered, fieldTypeMap);

        String setClause = filtered.keySet().stream()
                .map(f -> "`" + f + "` = ?")
                .collect(Collectors.joining(", "));
        String sql = "UPDATE `" + tableName + "` SET " + setClause + " WHERE id = ?";

        List<Object> params = new ArrayList<>(filtered.values());
        params.add(id);
        jdbcTemplate.update(sql, params.toArray());

        // AFTER_UPDATE 触发器（异常不阻断）
        fireAfterCrudTriggers(entityCode, "UPDATE",
                buildContext(entityCode, "UPDATE", "AFTER", id, data));
    }

    /**
     * 删除记录。
     */
    public void delete(String entityCode, Long id) {
        EntityDesignDTO design = getDesignByCode(entityCode);

        // BEFORE_DELETE 触发器（异常则阻断）
        fireBeforeCrudTriggers(entityCode, "DELETE",
                buildContext(entityCode, "DELETE", "BEFORE", id, null));

        String sql = "DELETE FROM `" + design.getEntity().getTableName() + "` WHERE id = ?";
        jdbcTemplate.update(sql, id);

        // AFTER_DELETE 触发器（异常不阻断）
        fireAfterCrudTriggers(entityCode, "DELETE",
                buildContext(entityCode, "DELETE", "AFTER", id, null));
    }

    private EntityDesignDTO getDesignByCode(String entityCode) {
        LowCodeEntity entity = entityService.getOne(new LambdaQueryWrapper<LowCodeEntity>()
                .eq(LowCodeEntity::getCode, entityCode));
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityCode);
        }
        return entityService.getDesign(entity.getId());
    }

    /**
     * 将浏览器 ISO-8601 日期值转换为 MySQL DATE/DATETIME 可接受的格式。
     */
    private void normalizeDateValues(Map<String, Object> data, Map<String, String> fieldTypeMap) {
        for (Map.Entry<String, String> entry : fieldTypeMap.entrySet()) {
            String fieldType = entry.getValue();
            Object value = data.get(entry.getKey());
            if ((!"DATE".equals(fieldType) && !"DATETIME".equals(fieldType))
                    || !(value instanceof String strValue) || !strValue.contains("T")) {
                continue;
            }
            String[] parts = strValue.split("T", 2);
            if ("DATE".equals(fieldType)) {
                data.put(entry.getKey(), parts[0]);
                continue;
            }
            String timePart = parts[1].replace("Z", "");
            int fractionIndex = timePart.indexOf('.');
            if (fractionIndex >= 0) {
                timePart = timePart.substring(0, fractionIndex);
            }
            data.put(entry.getKey(), parts[0] + " " + timePart);
        }
    }

    // ==================== CRUD 触发器钩子 ====================

    private Map<String, Object> buildContext(String entityCode, String operation, String timing,
                                             Long id, Map<String, Object> data) {
        Map<String, Object> context = new HashMap<>();
        context.put("entityCode", entityCode);
        context.put("operation", operation);
        context.put("timing", timing);
        context.put("eventType", timing + "_" + operation);
        if (id != null) {
            context.put("id", id);
        }
        if (data != null) {
            context.put("data", data);
        }
        return context;
    }

    /**
     * 触发 BEFORE CRUD 触发器：异常向上抛出以阻断主 CRUD 操作。
     */
    private void fireBeforeCrudTriggers(String entityCode, String operation, Map<String, Object> context) {
        for (LowCodeTrigger trigger : listMatchingCrudTriggers(entityCode, operation, "BEFORE")) {
            // BEFORE 触发器异常直接抛出，阻断主操作
            triggerService.executeTrigger(trigger.getCode(), context);
        }
    }

    /**
     * 触发 AFTER CRUD 触发器：异常仅记录日志，不阻断主 CRUD 操作。
     */
    private void fireAfterCrudTriggers(String entityCode, String operation, Map<String, Object> context) {
        for (LowCodeTrigger trigger : listMatchingCrudTriggers(entityCode, operation, "AFTER")) {
            try {
                triggerService.executeTrigger(trigger.getCode(), context);
            } catch (Exception e) {
                log.error("AFTER CRUD 触发器执行失败，已忽略: trigger={}, entityCode={}, operation={}",
                        trigger.getCode(), entityCode, operation, e);
            }
        }
    }

    /**
     * 查询匹配的 CRUD 触发器列表。触发器基础设施异常不阻断主 CRUD（返回空列表）。
     */
    private List<LowCodeTrigger> listMatchingCrudTriggers(String entityCode, String operation, String timing) {
        try {
            List<LowCodeTrigger> triggers = triggerService.list(new LambdaQueryWrapper<LowCodeTrigger>()
                    .eq(LowCodeTrigger::getType, "CRUD")
                    .eq(LowCodeTrigger::getStatus, "ACTIVE"));
            List<LowCodeTrigger> matched = new ArrayList<>();
            for (LowCodeTrigger trigger : triggers) {
                if (crudTriggerExecutor.matches(trigger, entityCode, operation, timing)) {
                    matched.add(trigger);
                }
            }
            return matched;
        } catch (Exception e) {
            log.error("查询 CRUD 触发器失败，已忽略: entityCode={}, operation={}", entityCode, operation, e);
            return List.of();
        }
    }
}
