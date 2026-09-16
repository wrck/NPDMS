package com.dp.plat.lowcode.engine;

import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeRelation;
import java.util.List;
import java.util.Map;

/**
 * DDL 生成器接口。
 *
 * <p>抽象 DDL 生成逻辑，支持不同数据库方言。
 * 已实现：{@link MySQLDdlGenerator}（默认）、{@link PostgreSQLDdlGenerator}、
 * {@link SqlServerDdlGenerator}。方言选择由 {@code DdlGeneratorFactory} 按 dbType 解析。</p>
 */
public interface DdlGenerator {

    /**
     * 返回此生成器支持的数据库方言标识（小写，与 LowCodeDataSource.dbType 对齐）。
     *
     * <p>取值：mysql / postgresql / sqlserver</p>
     *
     * @return 方言标识
     */
    String getDialect();

    /**
     * 生成 CREATE TABLE 语句（含字段、主键、索引、外键约束）。
     *
     * @param entity    实体定义
     * @param fields    字段列表
     * @param relations 关联列表（可为空）
     * @return 完整 CREATE TABLE SQL
     */
    String generateCreateTable(LowCodeEntity entity, List<LowCodeField> fields, List<LowCodeRelation> relations);

    /**
     * 生成 CREATE TABLE 语句（含字段、主键、索引、外键约束）。
     *
     * <p>支持通过 {@code entityIdToTableName} 映射（toEntityId → 物理表名）正确推导
     * 外键目标表名，避免依赖字段名约定猜测目标表。</p>
     *
     * <p>默认实现忽略映射，委托给 3 参版本以保持向后兼容；建议各方言实现重写以使用映射。</p>
     *
     * @param entity              实体定义
     * @param fields              字段列表
     * @param relations           关联列表（可为空）
     * @param entityIdToTableName toEntityId → 物理表名 映射（可为空）
     * @return 完整 CREATE TABLE SQL
     */
    default String generateCreateTable(LowCodeEntity entity, List<LowCodeField> fields,
                                        List<LowCodeRelation> relations, Map<Long, String> entityIdToTableName) {
        return generateCreateTable(entity, fields, relations);
    }

    /**
     * 生成 ALTER TABLE ADD COLUMN 语句。
     *
     * @param tableName 物理表名
     * @param field     新增字段
     * @return ALTER TABLE SQL
     */
    String generateAddColumn(String tableName, LowCodeField field);

    /**
     * 生成 ALTER TABLE DROP COLUMN 语句。
     *
     * @param tableName  物理表名
     * @param columnName 列名
     * @return ALTER TABLE SQL
     */
    String generateDropColumn(String tableName, String columnName);

    /**
     * 生成 CREATE INDEX 语句。
     *
     * @param tableName 物理表名
     * @param indexName 索引名
     * @param columnNames 索引列名列表
     * @param isUnique   是否唯一索引
     * @return CREATE INDEX SQL
     */
    String generateCreateIndex(String tableName, String indexName, List<String> columnNames, boolean isUnique);

    /**
     * 生成多对多中间表 CREATE TABLE 语句。
     *
     * @param junctionTable 中间表名
     * @param fromTableName 源表名
     * @param toTableName   目标表名
     * @param fromFieldName 源外键字段名
     * @param toFieldName   目标外键字段名
     * @param onDelete      级联删除策略
     * @return CREATE TABLE SQL
     */
    String generateJunctionTable(String junctionTable, String fromTableName, String toTableName,
                                  String fromFieldName, String toFieldName, String onDelete);

    /**
     * 生成 DROP INDEX 语句
     * @param tableName 表名
     * @param indexName 索引名
     * @return DROP INDEX SQL
     */
    String generateDropIndex(String tableName, String indexName);

    /**
     * 生成 ALTER COLUMN 语句（修改列类型/可空性）
     * @param tableName 表名
     * @param field 字段定义（新定义）
     * @return ALTER TABLE MODIFY COLUMN SQL
     */
    String generateAlterColumn(String tableName, com.dp.plat.lowcode.entity.LowCodeField field);
}
