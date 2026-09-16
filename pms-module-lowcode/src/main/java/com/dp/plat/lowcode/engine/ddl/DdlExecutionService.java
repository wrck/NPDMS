package com.dp.plat.lowcode.engine.ddl;

import java.util.List;

/**
 * DDL 执行服务接口 — 负责 DDL 的安全校验、备份、执行、日志记录
 */
public interface DdlExecutionService {

    /**
     * 执行 CREATE TABLE（含中间表）
     * @param entityId 实体 ID
     * @param confirmDrop 预留参数（CREATE 不需要，保持接口一致性）
     * @return 执行的 SQL 列表
     */
    List<String> executeCreate(Long entityId, boolean confirmDrop);

    /**
     * 执行增量 ALTER（对比字段差异，生成 ALTER TABLE ADD/DROP/MODIFY COLUMN）
     * @param entityId 实体 ID
     * @param confirmDrop 是否确认 DROP COLUMN
     * @return 执行的 SQL 列表
     */
    List<String> executeAlter(Long entityId, boolean confirmDrop);

    /**
     * 校验 DDL SQL 安全性
     * @param ddlSql DDL SQL
     * @throws DdlSecurityException 当包含禁止语句时
     */
    void validateBeforeExecution(String ddlSql);

    /**
     * 备份表结构（SHOW CREATE TABLE）
     * @param entityId 实体 ID
     * @param tableName 表名
     * @param backupType 备份类型
     */
    void backupTableStructure(Long entityId, String tableName, String backupType);

    /**
     * 判断表是否已存在
     * @param tableName 表名
     * @return true=已存在
     */
    boolean tableExists(String tableName);

    /**
     * 查询指定实体的 DDL 备份记录列表（按时间倒序）。
     * @param entityId 实体 ID
     * @return 备份记录列表
     */
    List<DdlBackup> listBackups(Long entityId);

    /**
     * 回滚最近一次 DDL 操作（基于 backup 表，按 id 倒序取最新一条）。
     * @param entityId 实体 ID
     * @return 回滚的 DDL 备份类型（CREATE/ALTER/DROP_COLUMN）
     */
    String rollbackLastDdl(Long entityId);

    /**
     * 回滚指定备份记录。
     * @param backupId 备份记录 ID
     */
    void rollbackByBackupId(Long backupId);
}

