-- =============================================================
-- V283__init_workflow_approval_center.sql
-- 迁移自源工程 V76__create_approval_center.sql（原样复制，列名适配）。
-- 统一审批中心（Story 6）：审批记录/节点/历史/字段权限 4 表，
-- 供 pms-module-workflow 实体映射（cn.iocoder.yudao.module.pms.workflow.entity.*）。
--
-- 关联设计文档：§2.2 ApprovalRecord/ApprovalFieldPermission（行 214-243）、
--              §3.5 审批中心统一规则（行 429-500）、§5.7 统一审批中心 API（行 1080-1147）、
--              §6.9（行 1565-1648）
--
-- 列名适配说明：源表 create_by/update_by 重命名为 creator/updater，
-- 与 yudao BaseDO（cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO）
-- 的 creator/updater 列名直接对齐（避免另做 V281 式重命名）；
-- 审计字段类型 VARCHAR(64)，与 BaseEntity 中 String 类型保持一致。
--
-- 状态机：[DRAFT] ──提交──► [PENDING] ──通过──► [APPROVED]
--                          │
--             ┌────────────┼────────────┐
--             ▼            ▼            ▼
--        [REJECTED]   [WITHDRAWN]   [TIMEOUT]
--             │ 重新提交（round+1，复用原记录）
--             ▼
--        [PENDING]
-- =============================================================

-- -------------------------------------------------------------
-- 1. 统一审批记录表 wf_approval_record
--    approval_type：PROJECT/TASK/DELIVERABLE/RISK/ISSUE/CHANGE/RESOURCE/COST/PHASE_EXIT/BASELINE_CHANGE
--    status：PENDING/APPROVED/REJECTED/WITHDRAWN/TIMEOUT
--    round：审批轮次（退回后重新提交 +1，复用原记录）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_approval_record` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `approval_type`       VARCHAR(32)  NOT NULL COMMENT 'PROJECT/TASK/DELIVERABLE/RISK/ISSUE/CHANGE/RESOURCE/COST/PHASE_EXIT/BASELINE_CHANGE',
    `business_id`         BIGINT       NOT NULL COMMENT '业务对象ID',
    `business_code`       VARCHAR(64)  DEFAULT NULL COMMENT '业务编码冗余',
    `project_id`          BIGINT       DEFAULT NULL COMMENT '项目维度',
    `process_instance_id` VARCHAR(64)  DEFAULT NULL COMMENT 'Flowable流程实例ID',
    `title`               VARCHAR(200) NOT NULL,
    `submitter_id`        BIGINT       NOT NULL,
    `submitter_name`      VARCHAR(64)  DEFAULT NULL,
    `current_node_id`     VARCHAR(64)  DEFAULT NULL COMMENT '当前节点ID（Flowable）',
    `current_node_name`   VARCHAR(64)  DEFAULT NULL,
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN/TIMEOUT',
    `round`               INT          NOT NULL DEFAULT 1 COMMENT '审批轮次',
    `submitted_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at`        DATETIME     DEFAULT NULL,
    `timeout_at`          DATETIME     DEFAULT NULL COMMENT '超时时间点',
    `escalated`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已升级',
    `creator`             VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updater`             VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time`         DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    `version`             INT          NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    PRIMARY KEY (`id`),
    KEY `idx_business_type_id` (`approval_type`, `business_id`),
    KEY `idx_project_status` (`project_id`, `status`),
    KEY `idx_submitter_status` (`submitter_id`, `status`),
    KEY `idx_status_timeout` (`status`, `timeout_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一审批记录';

-- -------------------------------------------------------------
-- 2. 审批节点表 wf_approval_node
--    节点顺序流转：当前节点通过后激活 node_order+1 的节点。
--    approver_id 与 approver_role 二选一（多选一时存实际处理人）。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_approval_node` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `record_id`          BIGINT      NOT NULL,
    `node_name`          VARCHAR(64) NOT NULL,
    `node_order`         INT         NOT NULL COMMENT '节点顺序',
    `approver_id`        BIGINT      DEFAULT NULL COMMENT '指定审批人',
    `approver_role`      VARCHAR(32) DEFAULT NULL COMMENT '审批角色（多选一）',
    `status`             VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    `approver_actual_id` BIGINT      DEFAULT NULL COMMENT '实际处理人',
    `opinion`            VARCHAR(500) DEFAULT NULL,
    `operated_at`        DATETIME    DEFAULT NULL,
    `timeout_at`         DATETIME    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_record_order` (`record_id`, `node_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点';

-- -------------------------------------------------------------
-- 3. 审批历史表 wf_approval_history
--    记录每轮每次操作（节点、操作人、动作、意见、时间戳），支持多轮次追溯。
--    action：SUBMIT/APPROVE/REJECT/WITHDRAW/RESUBMIT/ESCALATE/TIMEOUT
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_approval_history` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `record_id`     BIGINT      NOT NULL,
    `round`         INT         NOT NULL,
    `node_name`     VARCHAR(64) NOT NULL,
    `operator_id`   BIGINT      NOT NULL,
    `operator_name` VARCHAR(64) DEFAULT NULL,
    `action`        VARCHAR(20) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/WITHDRAW/RESUBMIT/ESCALATE/TIMEOUT',
    `opinion`       VARCHAR(500) DEFAULT NULL,
    `operated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_record_round_time` (`record_id`, `round`, `operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批历史';

-- -------------------------------------------------------------
-- 4. 审批敏感字段权限表 wf_approval_field_permission
--    permission：VISIBLE/MASKED/HIDDEN
--    mask_pattern：phone-mask/amount-mask/email-mask/custom
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_approval_field_permission` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `approval_node_id` BIGINT       NOT NULL COMMENT '关联审批节点（或节点模板）',
    `entity_type`      VARCHAR(128) NOT NULL COMMENT '业务实体类名',
    `field_name`       VARCHAR(64)  NOT NULL,
    `permission`       VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE/MASKED/HIDDEN',
    `mask_pattern`     VARCHAR(64)  DEFAULT NULL COMMENT '脱敏规则：phone-mask/amount-mask/email-mask/custom',
    `custom_pattern`   VARCHAR(128) DEFAULT NULL COMMENT '自定义正则（当 mask_pattern=custom）',
    `creator`          VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updater`          VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time`      DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    `version`          INT          NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    PRIMARY KEY (`id`),
    KEY `idx_node_entity` (`approval_node_id`, `entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批敏感字段权限';
