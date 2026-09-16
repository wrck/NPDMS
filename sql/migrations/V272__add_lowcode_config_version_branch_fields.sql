-- 批次5-T1：版本树分支支持 — 为 low_config_version 添加 parentId/branch/tag 字段
-- 借鉴 git 的 parent commit + branch + tag 模型，支持真正的版本分支树（非线性链）

ALTER TABLE low_config_version
    ADD COLUMN parent_version_id BIGINT NULL COMMENT '父版本 ID（null=根版本，指向 base 版本构建分支树）' AFTER environment,
    ADD COLUMN branch VARCHAR(64) NOT NULL DEFAULT 'main' COMMENT '分支名（默认 main，从某版本创建分支时指定）' AFTER parent_version_id,
    ADD COLUMN tags VARCHAR(128) NULL COMMENT '标签（逗号分隔，标记里程碑如 v1.0-release/审核通过）' AFTER branch;

-- 为现有版本数据补全 parent_version_id：按 configType+configId 分组，version 升序，
-- 每个版本的 parent 指向前一个版本（构建线性链作为初始树结构）
-- 第一个版本（version 最小）的 parent_version_id 保持 NULL（根节点）
UPDATE low_config_version v
JOIN (
    SELECT id,
           config_type,
           config_id,
           version,
           LAG(id) OVER (PARTITION BY config_type, config_id ORDER BY version) AS prev_id
    FROM low_config_version
) t ON v.id = t.id
SET v.parent_version_id = t.prev_id
WHERE v.parent_version_id IS NULL
  AND t.prev_id IS NOT NULL;

-- 索引：按 configType+configId+branch 查询分支版本（高频查询）
CREATE INDEX idx_config_version_branch
    ON low_config_version (config_type, config_id, branch);

-- 索引：按 parentId 查询子版本（构建树时高频查询）
CREATE INDEX idx_config_version_parent
    ON low_config_version (parent_version_id);
