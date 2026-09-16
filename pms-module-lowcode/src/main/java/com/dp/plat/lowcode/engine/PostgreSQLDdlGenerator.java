package com.dp.plat.lowcode.engine;

import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeRelation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PostgreSQL DDL 生成器（批次3-T10）。
 *
 * <p>将低代码实体/字段/关联定义转换为 PostgreSQL DDL 语句。
 * 标识符用双引号引用，类型映射遵循 PostgreSQL 惯例。
 * 主键、唯一约束、索引、外键语义与 {@link MySQLDdlGenerator} 对齐，
 * 仅语法与类型映射不同。</p>
 *
 * <p>字段类型映射表：
 * <ul>
 *   <li>STRING → VARCHAR(length)</li>
 *   <li>INTEGER → INTEGER</li>
 *   <li>LONG → BIGINT</li>
 *   <li>DECIMAL → NUMERIC(length, scale)</li>
 *   <li>BOOLEAN → BOOLEAN</li>
 *   <li>DATE → DATE</li>
 *   <li>DATETIME → TIMESTAMP</li>
 *   <li>TEXT → TEXT</li>
 * </ul></p>
 */
@Component
public class PostgreSQLDdlGenerator implements DdlGenerator {

    @Override
    public String getDialect() {
        return "postgresql";
    }

    @Override
    public String generateCreateTable(LowCodeEntity entity, List<LowCodeField> fields,
                                      List<LowCodeRelation> relations) {
        return generateCreateTable(entity, fields, relations, null);
    }

    @Override
    public String generateCreateTable(LowCodeEntity entity, List<LowCodeField> fields,
                                      List<LowCodeRelation> relations, Map<Long, String> entityIdToTableName) {
        String tableName = entity.getTableName();
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE \"").append(tableName).append("\" (\n");

        // 字段定义
        List<String> columnDefs = fields.stream()
                .map(this::buildColumnDef)
                .collect(Collectors.toList());

        // 主键（与 MySQL 实现一致：从设计字段中筛选 primaryKey=1 的列）
        List<String> pkColumns = fields.stream()
                .filter(f -> f.getPrimaryKey() == 1)
                .map(LowCodeField::getName)
                .toList();
        if (!pkColumns.isEmpty()) {
            columnDefs.add("PRIMARY KEY (\"" + String.join("\", \"", pkColumns) + "\")");
        }

        // 唯一约束
        fields.stream()
                .filter(f -> f.getUniqueFlag() == 1 && f.getPrimaryKey() == 0)
                .forEach(f -> columnDefs.add(
                        "CONSTRAINT \"uk_" + tableName + "_" + f.getName()
                                + "\" UNIQUE (\"" + f.getName() + "\")"));

        // 普通索引（PostgreSQL 不支持内联 KEY 定义，需通过 CREATE INDEX 单独创建，
        // 此处仅外键约束放表级；索引在 DdlExecutionService 层通过 generateCreateIndex 生成）
        if (relations != null) {
            relations.stream()
                    .filter(r -> !"MANY_TO_MANY".equals(r.getRelationType()))
                    .forEach(r -> columnDefs.add(
                            buildForeignKeyConstraint(r, tableName, entityIdToTableName)));
        }

        sql.append(String.join(",\n", columnDefs));
        sql.append("\n)");
        return sql.toString();
    }

    @Override
    public String generateAddColumn(String tableName, LowCodeField field) {
        return "ALTER TABLE \"" + tableName + "\" ADD COLUMN " + buildColumnDef(field);
    }

    @Override
    public String generateDropColumn(String tableName, String columnName) {
        return "ALTER TABLE \"" + tableName + "\" DROP COLUMN IF EXISTS \"" + columnName + "\"";
    }

    @Override
    public String generateCreateIndex(String tableName, String indexName,
                                       List<String> columnNames, boolean isUnique) {
        String cols = columnNames.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", "));
        return "CREATE " + (isUnique ? "UNIQUE " : "") + "INDEX \"" + indexName + "\" ON \""
                + tableName + "\" (" + cols + ")";
    }

    @Override
    public String generateJunctionTable(String junctionTable, String fromTableName, String toTableName,
                                         String fromFieldName, String toFieldName, String onDelete) {
        return "CREATE TABLE \"" + junctionTable + "\" (\n" +
                "  \"" + fromFieldName + "\" BIGINT NOT NULL,\n" +
                "  \"" + toFieldName + "\" BIGINT NOT NULL,\n" +
                "  PRIMARY KEY (\"" + fromFieldName + "\", \"" + toFieldName + "\"),\n" +
                "  CONSTRAINT \"fk_" + fromFieldName + "\" FOREIGN KEY (\"" + fromFieldName +
                "\") REFERENCES \"" + fromTableName + "\"(\"id\") ON DELETE " +
                onDelete.replace("_", " ") + ",\n" +
                "  CONSTRAINT \"fk_" + toFieldName + "\" FOREIGN KEY (\"" + toFieldName +
                "\") REFERENCES \"" + toTableName + "\"(\"id\") ON DELETE " +
                onDelete.replace("_", " ") + "\n" +
                ")";
    }

    @Override
    public String generateDropIndex(String tableName, String indexName) {
        // PostgreSQL DROP INDEX 不需要 ON table
        return "DROP INDEX IF EXISTS \"" + indexName + "\"";
    }

    @Override
    public String generateAlterColumn(String tableName, LowCodeField field) {
        // PostgreSQL 用 ALTER COLUMN ... TYPE 修改列类型，可空性单独 ALTER
        return "ALTER TABLE \"" + tableName + "\" ALTER COLUMN \"" + field.getName()
                + "\" TYPE " + mapFieldType(field)
                + ", ALTER COLUMN \"" + field.getName() + "\" "
                + (field.getNullable() == 1 ? "DROP NOT NULL" : "SET NOT NULL");
    }

    private String buildColumnDef(LowCodeField field) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"").append(field.getName()).append("\" ");
        sb.append(mapFieldType(field));
        sb.append(field.getNullable() == 1 ? " NULL" : " NOT NULL");
        if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(field.getDefaultValue());
        }
        return sb.toString();
    }

    private String mapFieldType(LowCodeField field) {
        String type = field.getFieldType();
        return switch (type) {
            case "STRING" -> "VARCHAR(" + (field.getLength() != null ? field.getLength() : 255) + ")";
            case "INTEGER" -> "INTEGER";
            case "LONG" -> "BIGINT";
            case "DECIMAL" -> "NUMERIC(" + (field.getLength() != null ? field.getLength() : 10)
                    + "," + (field.getScale() != null ? field.getScale() : 2) + ")";
            case "BOOLEAN" -> "BOOLEAN";
            case "DATE" -> "DATE";
            case "DATETIME" -> "TIMESTAMP";
            case "TEXT" -> "TEXT";
            default -> throw new IllegalArgumentException("不支持的字段类型: " + type);
        };
    }

    private String buildForeignKeyConstraint(LowCodeRelation relation, String currentTableName,
                                              Map<Long, String> entityIdToTableName) {
        String refTable;
        if (relation.getFromEntityId() != null && relation.getFromEntityId().equals(relation.getToEntityId())) {
            refTable = currentTableName;
        } else if (entityIdToTableName != null && relation.getToEntityId() != null
                && entityIdToTableName.containsKey(relation.getToEntityId())) {
            refTable = entityIdToTableName.get(relation.getToEntityId());
        } else {
            refTable = "pms_lc_" + relation.getFromFieldName().replace("_id", "");
        }
        String onDelete = "ON DELETE " + relation.getOnDelete().replace("_", " ");
        String onUpdate = "ON UPDATE " + relation.getOnUpdate().replace("_", " ");
        return "CONSTRAINT \"fk_" + relation.getFromFieldName() + "\" FOREIGN KEY (\""
                + relation.getFromFieldName() + "\") REFERENCES \"" + refTable + "\"(\"id\") "
                + onDelete + " " + onUpdate;
    }
}
