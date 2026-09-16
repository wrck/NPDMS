-- =============================================================
-- V251__init_lowcode_tables.sql
-- 初始化低代码配置模块的 4 张配置表：
--   low_form          表单配置
--   low_list          列表配置
--   low_tab           标签页配置
--   low_related_page  关联页配置
--
-- <p>每张表均包含：唯一编码 code、名称 name、描述 description、
-- JSON 格式的配置内容 *_config、版本号 version（乐观锁）、状态 status
-- （DRAFT/PUBLISHED/ARCHIVED）、业务类型 biz_type，以及与 BaseEntity
-- 对齐的审计字段（create_by/create_time/update_by/update_time/deleted）。</p>
--
-- <p>审计字段 create_by/update_by 使用 VARCHAR(64) 与 BaseEntity 中
-- String 类型保持一致（存储用户名而非用户 ID）。</p>
--
-- <p>版本号说明：tasks.md 原计划 SubTask 24.3 使用 V26，但 V26 已被
-- Task 14.2（add_version_fields 乐观锁字段）占用，故低代码配置表
-- 迁移顺延为 V27；Task 34.3（sys_feedback）相应顺延为 V28。</p>
-- =============================================================

-- -------------------------------------------------------------
-- 1. 表单配置表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `low_form`;
CREATE TABLE `low_form` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '表单编码（唯一）',
    `name`        VARCHAR(128) NOT NULL COMMENT '表单名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
    `form_config` JSON         NOT NULL COMMENT '表单配置 JSON Schema（fields + layout）',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
    `biz_type`    VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    `create_by`   VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_form_code` (`code`),
    KEY `idx_form_status` (`status`),
    KEY `idx_form_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码表单配置';

-- -------------------------------------------------------------
-- 2. 列表配置表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `low_list`;
CREATE TABLE `low_list` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '列表编码',
    `name`        VARCHAR(128) NOT NULL COMMENT '列表名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
    `list_config` JSON         NOT NULL COMMENT '列表配置 JSON Schema（columns/filters/operations/pagination/searchApi）',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
    `biz_type`    VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    `create_by`   VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_list_code` (`code`),
    KEY `idx_list_status` (`status`),
    KEY `idx_list_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码列表配置';

-- -------------------------------------------------------------
-- 3. 标签页配置表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `low_tab`;
CREATE TABLE `low_tab` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '标签页编码',
    `name`        VARCHAR(128) NOT NULL COMMENT '标签页名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
    `tab_config`  JSON         NOT NULL COMMENT '标签页配置（tabs 数组，每个 tab 含 title/pageCode/type）',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
    `biz_type`    VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    `create_by`   VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_code` (`code`),
    KEY `idx_tab_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码标签页配置';

-- -------------------------------------------------------------
-- 4. 关联页配置表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `low_related_page`;
CREATE TABLE `low_related_page` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`          VARCHAR(64)  NOT NULL COMMENT '关联页编码',
    `name`          VARCHAR(128) NOT NULL COMMENT '关联页名称',
    `description`   VARCHAR(512) DEFAULT NULL COMMENT '描述',
    `related_config` JSON        NOT NULL COMMENT '关联页配置（关联关系 + 关联页面引用）',
    `version`       INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
    `biz_type`      VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    `create_by`     VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`     VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_related_code` (`code`),
    KEY `idx_related_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码关联页配置';
