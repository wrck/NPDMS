-- P09 统一业务模型：结果订阅、执行证据与证据影响
-- 订阅：允许复用的结果订阅（选择策略沿用既有五类语义，轮次以形成序号为边界）。
CREATE TABLE pms_plat_result_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_code VARCHAR(64) NOT NULL,
    subscriber_kind VARCHAR(16) NOT NULL COMMENT '订阅者类型：TASK/STAGE/NODE',
    subscriber_node_key VARCHAR(128) NOT NULL,
    result_type VARCHAR(128) NOT NULL,
    owner_module VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    expected_object_ids JSON NULL COMMENT 'ALL_EXPECTED 必填的期望对象集合',
    acquisition VARCHAR(24) NOT NULL COMMENT 'NEW_RESULT/REUSE_EXISTING/PINNED_RESULT',
    selection VARCHAR(24) NOT NULL COMMENT 'EXACT_ONE/ANY_MATCHING/ALL_EXPECTED',
    validity_policy VARCHAR(24) NOT NULL COMMENT 'ANY/CURRENT_VALID',
    pinned_result_id VARCHAR(64) NULL,
    round_no BIGINT NOT NULL DEFAULT 1,
    formation_baseline BIGINT NOT NULL COMMENT '轮次形成序号下界（不含），取建立时结果最大序号',
    project_stable_ref VARCHAR(128) NULL COMMENT '建立订阅时冻结的项目稳定引用（证据写入用）',
    plan_version VARCHAR(64) NULL COMMENT '建立订阅时冻结的计划版本',
    rule_version VARCHAR(64) NULL COMMENT '建立订阅时冻结的规则版本',
    status VARCHAR(16) NOT NULL DEFAULT 'COLLECTING' COMMENT 'COLLECTING/SATISFIED/WAITING/AMBIGUOUS/UNAVAILABLE',
    last_examined BIGINT NULL,
    last_eligible BIGINT NULL,
    missing_objects JSON NULL,
    version INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_result_subscription_code (tenant_id, subscription_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '统一模型结果订阅';

-- 执行证据：中性实例/节点/轮次标识 + 采纳引用；原生执行标识不进入。
-- 独立业务（无项目编排）产生的证据 project_stable_ref/plan_version 为空。
CREATE TABLE pms_plat_execution_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_stable_ref VARCHAR(128) NULL,
    plan_version VARCHAR(64) NULL,
    node_key VARCHAR(128) NOT NULL,
    round_no BIGINT NOT NULL,
    rule_version VARCHAR(64) NULL,
    decision VARCHAR(16) NOT NULL,
    subscription_id BIGINT NULL,
    adopted_fact_refs JSON NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    KEY idx_evidence_node (tenant_id, project_stable_ref, node_key, round_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '统一模型执行证据';

-- 证据-结果采纳引用：失效影响按引用回查。
CREATE TABLE pms_plat_evidence_result_ref (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    result_id VARCHAR(64) NOT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_evidence_result (evidence_id, result_id),
    KEY idx_evidence_result_id (tenant_id, result_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '执行证据采纳结果引用';

-- 证据影响：原完成证据失效时记录历史影响，不覆盖原结论、不自动重开。
CREATE TABLE pms_plat_evidence_impact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    result_id VARCHAR(64) NOT NULL,
    impact VARCHAR(32) NOT NULL COMMENT 'RESULT_INVALIDATED 等',
    detail VARCHAR(255) NULL,
    recorded_at DATETIME NOT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    KEY idx_impact_evidence (tenant_id, evidence_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '执行证据历史影响';
