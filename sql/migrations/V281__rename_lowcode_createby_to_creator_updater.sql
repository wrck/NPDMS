-- =============================================================
-- V281__rename_lowcode_createby_to_creator_updater.sql
-- 迁移自源工程 V92__rename_createby_updateby_to_creator_updater.sql（原样复制）。
-- 将 pms_* / sys_* / d365_* / demo_* 表的 create_by 重命名为 creator、
-- update_by 重命名为 updater，与 yudao BaseDO（cn.iocoder.yudao.framework.mybatis
-- .core.dataobject.BaseDO）的 creator/updater 列名对齐。
--
-- 目标库适配说明：
--   - 本仓库自有表（V1-V250）已使用 creator/updater，information_schema
--     检测不到 create_by 列，过程对其为 no-op；
--   - 仅对本次迁移的 low_* 表（V251-V280 建立，create_by/update_by
--     命名）实际执行重命名，使 pms-module-lowcode 实体映射生效；
--   - 幂等：列不存在或目标列已存在时跳过，重复执行安全。
-- =============================================================

-- =============================================================
-- V281__rename_lowcode_createby_to_creator_updater.sql
-- 将 PMS 业务表 / sys_* 表 / d365_* 表 / demo_* 表的
-- create_by 列重命名为 creator，update_by 列重命名为 updater
--
-- 背景：
--   PMS BaseEntity 已继承 yudao BaseDO，BaseDO 的字段为
--   creator / updater（对应数据库列 creator / updater）。
--   但历史 PMS 表使用 create_by / update_by 列名，
--   导致 MyBatis-Plus 查询时报 "Unknown column 'creator' in 'field list'"。
--
-- 影响范围：
--   所有具有 create_by / update_by 列的 pms_*、sys_*、d365_*、demo_* 表。
--   不影响 sms_*、t_* 等无 Java 实体映射的遗留表。
--
-- 幂等性：通过 information_schema 检测列是否存在，仅在列存在时执行 ALTER。
-- =============================================================

DROP PROCEDURE IF EXISTS pms_v92_rename_audit_columns;
DELIMITER $$
CREATE PROCEDURE pms_v92_rename_audit_columns()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tbl_name VARCHAR(64);
    DECLARE col_name VARCHAR(64);
    DECLARE new_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT TABLE_NAME, COLUMN_NAME
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND COLUMN_NAME IN ('create_by', 'update_by')
          AND (TABLE_NAME LIKE 'pms\_%'
            OR TABLE_NAME LIKE 'sys\_%'
            OR TABLE_NAME LIKE 'd365\_%'
            OR TABLE_NAME LIKE 'demo\_%');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tbl_name, col_name;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF col_name = 'create_by' THEN
            SET new_name = 'creator';
        ELSE
            SET new_name = 'updater';
        END IF;

        -- 检查目标列是否已存在（避免重复执行报错）
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = tbl_name
              AND COLUMN_NAME = new_name
        ) THEN
            SET @ddl = CONCAT(
                'ALTER TABLE `', tbl_name, '` ',
                'CHANGE COLUMN `', col_name, '` `', new_name, '` VARCHAR(64) NULL',
                CASE new_name
                    WHEN 'creator' THEN ' COMMENT ''创建人（用户标识）'''
                    WHEN 'updater' THEN ' COMMENT ''更新人（用户标识）'''
                    ELSE ''
                END
            );
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL pms_v92_rename_audit_columns();
DROP PROCEDURE IF EXISTS pms_v92_rename_audit_columns;

-- =============================================================
-- 自检注释
-- =============================================================
-- 预期变更：
--   所有 pms_* / sys_* / d365_* / demo_* 表的 create_by → creator
--   所有 pms_* / sys_* / d365_* / demo_* 表的 update_by → updater
--   列类型保持 VARCHAR(64) NULL
--
-- 兼容性说明：
--   - 重复执行安全：information_schema 检测目标列是否存在
--   - 不影响 sms_* / t_* 等无实体映射的遗留表
--   - V90 数据迁移脚本引用 create_by/update_by 从 sys_* 表读取数据写入 system_* 表，
--     但 V90 已在本次迁移前执行完毕，不影响后续执行
-- =============================================================
-- 文件结束
