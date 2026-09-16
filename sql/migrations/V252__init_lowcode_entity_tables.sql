-- V29: 低代码数据建模表（实体/字段/关联）
-- 评审决策：支持复杂关联（多对多/自关联/级联删除）

-- 实体定义表
CREATE TABLE low_entity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '实体编码（唯一）',
    name VARCHAR(128) NOT NULL COMMENT '实体名称',
    table_name VARCHAR(64) NOT NULL COMMENT '物理表名',
    description VARCHAR(512) DEFAULT NULL COMMENT '描述',
    biz_type VARCHAR(64) DEFAULT NULL COMMENT '业务类型',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    UNIQUE KEY uk_table_name (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码实体定义';

-- 实体字段表
CREATE TABLE low_field (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    entity_id BIGINT NOT NULL COMMENT '所属实体ID',
    name VARCHAR(64) NOT NULL COMMENT '字段名（数据库列名）',
    label VARCHAR(128) NOT NULL COMMENT '字段显示名',
    field_type VARCHAR(32) NOT NULL COMMENT '字段类型: STRING/INTEGER/DECIMAL/BOOLEAN/DATE/DATETIME/TEXT/LONG',
    length INT DEFAULT NULL COMMENT '长度（STRING/DECIMAL 用）',
    scale INT DEFAULT NULL COMMENT '小数位数（DECIMAL 用）',
    nullable TINYINT NOT NULL DEFAULT 1 COMMENT '是否可空: 0否 1是',
    primary_key TINYINT NOT NULL DEFAULT 0 COMMENT '是否主键: 0否 1是',
    indexed TINYINT NOT NULL DEFAULT 0 COMMENT '是否索引: 0否 1是',
    unique_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否唯一: 0否 1是',
    default_value VARCHAR(256) DEFAULT NULL COMMENT '默认值',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_entity_id (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码实体字段';

-- 实体关联表（支持多对多/自关联/级联删除）
CREATE TABLE low_relation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    from_entity_id BIGINT NOT NULL COMMENT '源实体ID',
    to_entity_id BIGINT NOT NULL COMMENT '目标实体ID',
    relation_type VARCHAR(16) NOT NULL COMMENT '关联类型: ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE/MANY_TO_MANY',
    from_field_name VARCHAR(64) NOT NULL COMMENT '源端外键字段名',
    to_field_name VARCHAR(64) DEFAULT NULL COMMENT '目标端外键字段名（多对多用，中间表字段）',
    reverse_name VARCHAR(64) DEFAULT NULL COMMENT '反向关联名称',
    junction_table VARCHAR(64) DEFAULT NULL COMMENT '多对多中间表名',
    on_delete VARCHAR(16) NOT NULL DEFAULT 'RESTRICT' COMMENT '级联删除策略: CASCADE/SET_NULL/RESTRICT',
    on_update VARCHAR(16) NOT NULL DEFAULT 'RESTRICT' COMMENT '级联更新策略: CASCADE/RESTRICT',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_from_entity (from_entity_id),
    KEY idx_to_entity (to_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码实体关联关系';
