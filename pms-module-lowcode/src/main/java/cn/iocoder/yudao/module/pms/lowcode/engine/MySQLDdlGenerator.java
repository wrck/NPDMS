package cn.iocoder.yudao.module.pms.lowcode.engine;

import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeEntity;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeField;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeRelation;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL 8.0 DDL 生成器。
 *
 * <p>将低代码实体/字段/关联定义转换为标准 MySQL DDL 语句。
 * 支持字段类型映射、主键、索引、唯一约束、外键（含自关联与级联删除）。</p>
 *
 * <p>标记为 {@link Primary}：作为默认方言注入到 {@code DdlExecutionServiceImpl}，
 * 其他方言通过 {@code DdlGeneratorFactory} 按数据源 dbType 解析。</p>
 *
 * <p>字段类型映射表：
 * <ul>
 *   <li>STRING → VARCHAR(length)</li>
 *   <li>INTEGER → INT</li>
 *   <li>LONG → BIGINT</li>
 *   <li>DECIMAL → DECIMAL(length, scale)</li>
 *   <li>BOOLEAN → TINYINT(1)</li>
 *   <li>DATE → DATE</li>
 *   <li>DATETIME → DATETIME</li>
 *   <li>TEXT → TEXT</li>
 * </ul></p>
 */
@Primary
@Component
public class MySQLDdlGenerator implements DdlGenerator {

    @Override
    public String getDialect() {
        return "mysql";
    }

    @Override
    public String generateCreateTable(LowCodeEntity entity, List<LowCodeField> fields,
                                      List<LowCodeRelation> relations) {
        // 3 参版本：委托给 4 参版本（传空映射，外键目标表名走兜底逻辑）以保持向后兼容
        return generateCreateTable(entity, fields, relations, null);
    }

    @Override
    public String generateCreateTable(LowCodeEntity entity, List<LowCodeField> fields,
                                      List<LowCodeRelation> relations, Map<Long, String> entityIdToTableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE `").append(entity.getTableName()).append("` (\n");

        // 字段定义
        List<String> columnDefs = fields.stream()
                .map(this::buildColumnDef)
                .collect(Collectors.toList());

        // 主键
        List<String> pkColumns = fields.stream()
                .filter(f -> f.getPrimaryKey() == 1)
                .map(LowCodeField::getName)
                .toList();
        if (!pkColumns.isEmpty()) {
            columnDefs.add("PRIMARY KEY (`" + String.join("`, `", pkColumns) + "`)");
        }

        // 唯一约束
        fields.stream()
                .filter(f -> f.getUniqueFlag() == 1 && f.getPrimaryKey() == 0)
                .forEach(f -> columnDefs.add(
                        "UNIQUE KEY `uk_" + f.getName() + "` (`" + f.getName() + "`)"));

        // 普通索引（indexed=1 即生成，与 unique 独立：唯一字段若同时标记 indexed 也会额外生成普通索引）
        fields.stream()
                .filter(f -> f.getIndexed() == 1 && f.getPrimaryKey() == 0)
                .forEach(f -> columnDefs.add(
                        "KEY `idx_" + f.getName() + "` (`" + f.getName() + "`)"));

        // 外键约束（非多对多，多对多通过中间表实现）
        if (relations != null) {
            relations.stream()
                    .filter(r -> !"MANY_TO_MANY".equals(r.getRelationType()))
                    .forEach(r -> columnDefs.add(
                            buildForeignKeyConstraint(r, entity.getTableName(), entityIdToTableName)));
        }

        sql.append(String.join(",\n", columnDefs));
        sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return sql.toString();
    }

    @Override
    public String generateAddColumn(String tableName, LowCodeField field) {
        return "ALTER TABLE `" + tableName + "` ADD COLUMN " + buildColumnDef(field);
    }

    @Override
    public String generateDropColumn(String tableName, String columnName) {
        return "ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`";
    }

    @Override
    public String generateCreateIndex(String tableName, String indexName,
                                       List<String> columnNames, boolean isUnique) {
        String type = isUnique ? "UNIQUE INDEX" : "INDEX";
        String columns = columnNames.stream()
                .map(c -> "`" + c + "`")
                .collect(Collectors.joining(", "));
        return "CREATE " + type + " `" + indexName + "` ON `" + tableName + "` (" + columns + ")";
    }

    @Override
    public String generateJunctionTable(String junctionTable, String fromTableName, String toTableName,
                                         String fromFieldName, String toFieldName, String onDelete) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE `").append(junctionTable).append("` (\n");
        sql.append("  `").append(fromFieldName).append("` BIGINT NOT NULL,\n");
        sql.append("  `").append(toFieldName).append("` BIGINT NOT NULL,\n");
        sql.append("  PRIMARY KEY (`").append(fromFieldName).append("`, `").append(toFieldName).append("`),\n");
        sql.append("  FOREIGN KEY (`").append(fromFieldName).append("`) REFERENCES `")
                .append(fromTableName).append("`(`id`) ON DELETE ").append(onDelete).append(",\n");
        sql.append("  FOREIGN KEY (`").append(toFieldName).append("`) REFERENCES `")
                .append(toTableName).append("`(`id`) ON DELETE ").append(onDelete).append("\n");
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return sql.toString();
    }

    @Override
    public String generateDropIndex(String tableName, String indexName) {
        return "DROP INDEX `" + indexName + "` ON `" + tableName + "`";
    }

    @Override
    public String generateAlterColumn(String tableName, LowCodeField field) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE `").append(tableName).append("` MODIFY COLUMN ");
        sb.append(buildColumnDef(field));
        return sb.toString();
    }

    private String buildColumnDef(LowCodeField field) {
        StringBuilder sb = new StringBuilder();
        sb.append("`").append(field.getName()).append("` ");

        // 类型映射
        sb.append(mapFieldType(field));

        // 可空性
        sb.append(field.getNullable() == 1 ? " NULL" : " NOT NULL");

        // 默认值
        if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(field.getDefaultValue());
        }

        return sb.toString();
    }

    private String mapFieldType(LowCodeField field) {
        String type = field.getFieldType();
        return switch (type) {
            case "STRING" -> "VARCHAR(" + (field.getLength() != null ? field.getLength() : 255) + ")";
            case "INTEGER" -> "INT";
            case "LONG" -> "BIGINT";
            case "DECIMAL" -> "DECIMAL(" + (field.getLength() != null ? field.getLength() : 10)
                    + "," + (field.getScale() != null ? field.getScale() : 2) + ")";
            case "BOOLEAN" -> "TINYINT(1)";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "TEXT" -> "TEXT";
            default -> throw new IllegalArgumentException("不支持的字段类型: " + type);
        };
    }

    private String buildForeignKeyConstraint(LowCodeRelation relation, String currentTableName,
                                              Map<Long, String> entityIdToTableName) {
        String refTable;
        // 自关联：引用当前表
        if (relation.getFromEntityId() != null && relation.getFromEntityId().equals(relation.getToEntityId())) {
            refTable = currentTableName;
        } else if (entityIdToTableName != null && relation.getToEntityId() != null
                && entityIdToTableName.containsKey(relation.getToEntityId())) {
            // 优先使用调用方提供的 toEntityId → 物理表名 映射
            refTable = entityIdToTableName.get(relation.getToEntityId());
        } else {
            // 兜底：按 fromFieldName 约定推导（不完美，仅在未提供映射时使用）
            refTable = "pms_lc_" + relation.getFromFieldName().replace("_id", "");
        }

        // 将下划线分隔的策略名转为 SQL 语法（SET_NULL → SET NULL, NO_ACTION → NO ACTION）
        String onDelete = "ON DELETE " + relation.getOnDelete().replace("_", " ");
        String onUpdate = "ON UPDATE " + relation.getOnUpdate().replace("_", " ");
        return "CONSTRAINT `fk_" + relation.getFromFieldName() + "` FOREIGN KEY (`"
                + relation.getFromFieldName() + "`) REFERENCES `" + refTable + "`(`id`) "
                + onDelete + " " + onUpdate;
    }
}
