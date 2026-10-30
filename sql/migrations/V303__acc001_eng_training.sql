-- P0-1 现场培训（6.1 / ACC-01）整页承接（V303，前向、幂等）
-- 背景：现场培训前端/后端/表/菜单全无（核对记录
--       docs/reference/2026-09-19-项目交付全流程操作清单核对.md P0-1）。
-- 参照满意度公开问卷模式：外发 token 仅存 SHA-256 摘要，客户移动端确认后自动归档交付件。
-- 外部推送通道（短信/钉钉）未接入，外发动作提供链接复制，不伪造送达。

CREATE TABLE IF NOT EXISTS imp_eng_training (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '培训记录编码，项目内唯一',
  name varchar(255) NOT NULL COMMENT '培训名称',
  contact_name varchar(64) DEFAULT NULL COMMENT '客户联系人（自动带入用户联系人）',
  contact_phone varchar(32) DEFAULT NULL COMMENT '客户联系电话（自动带入）',
  training_types varchar(128) NOT NULL COMMENT '培训类型，逗号分隔：TECHNICAL_PRINCIPLE/PRODUCT_OPS/OTHER',
  training_time date NOT NULL COMMENT '培训时间',
  trainer_user_id bigint NOT NULL COMMENT '培训工程师用户编号（默认当前登录人）',
  trainer_name varchar(64) DEFAULT NULL COMMENT '培训工程师姓名快照',
  trainee_count int DEFAULT NULL COMMENT '参训人数',
  content text NULL COMMENT '培训内容',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已外发 2客户已确认 3已作废',
  sign_token_digest char(64) DEFAULT NULL COMMENT '外发令牌SHA-256摘要（原始令牌不落库）',
  token_expires_at datetime DEFAULT NULL COMMENT '外发令牌有效期',
  skill_rating varchar(32) DEFAULT NULL COMMENT '客户评价：培训工程师技术水平及表达能力',
  effect_rating varchar(32) DEFAULT NULL COMMENT '客户评价：培训内容及讲解效果',
  satisfaction_rating varchar(32) DEFAULT NULL COMMENT '客户评价：培训满意度',
  sign_opinion varchar(500) DEFAULT NULL COMMENT '客户综合意见',
  sign_confirmer_name varchar(64) DEFAULT NULL COMMENT '客户签字人',
  sign_time datetime DEFAULT NULL COMMENT '客户确认时间',
  file_url varchar(500) DEFAULT NULL COMMENT '培训记录表文件地址',
  file_name varchar(255) DEFAULT NULL COMMENT '培训记录表文件名',
  file_size bigint DEFAULT NULL COMMENT '培训记录表文件大小',
  file_checksum varchar(128) DEFAULT NULL COMMENT '培训记录表文件SHA-256校验值',
  version int NOT NULL DEFAULT 0,
  remark varchar(500) DEFAULT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_imp_eng_training_code (project_id, code),
  KEY idx_imp_eng_training_project (project_id),
  KEY idx_imp_eng_training_digest (sign_token_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='现场培训记录（ACC-01）';

-- 文档类型字典补充：现场培训记录（归档交付件使用）
INSERT IGNORE INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(21315, 6, '现场培训记录', 'TRAINING', 'pms_document_type', 0, 'success', '', 'ACC-01 现场培训确认后自动归档',
 'p001_v303', NOW(), 'p001_v303', NOW(), b'0');

-- 培训类型字典（Demo 6.1 三个固定选项）
INSERT IGNORE INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(993109100203, 'PMS-培训类型', 'pms_training_type', 0, '现场培训类型（Demo 6.1）',
 'p001_v303', NOW(), 'p001_v303', NOW(), b'0');

INSERT IGNORE INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(21330, 1, '技术原理类', 'TECHNICAL_PRINCIPLE', 'pms_training_type', 0, 'primary', '', '', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0'),
(21331, 2, '产品运维类', 'PRODUCT_OPS', 'pms_training_type', 0, 'success', '', '', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0'),
(21332, 3, '其它', 'OTHER', 'pms_training_type', 0, 'info', '', '', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0');

-- 交付件类型注释补充 TRAINING（培训确认后自动归档使用）
ALTER TABLE `imp_eng_deliverable`
  MODIFY COLUMN `deliverable_type` varchar(32) NOT NULL
  COMMENT '类型 DAILY 日报 / RECEIPT 签收单 / SERVICE 服务单 / COMPLETION 完工证明 / TEST 测试记录 / CONFIG 配置档案 / TRAINING 现场培训记录';

-- 菜单：页面 + 操作按钮（挂在项目交付目录 18000 下）
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(993109180002, '现场培训（6.1）', 'pms:imp-training:query', 2, 62, 18000, 'imp-training', 'ep:notebook',
 'pms/engineering/training/index', 'PmsImpTraining', 0, b'1', b'1', b'1',
 'p001_v303', NOW(), 'p001_v303', NOW(), b'0'),
(993109180003, '培训记录创建', 'pms:imp-training:create', 3, 1, 993109180002, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0'),
(993109180004, '培训记录更新', 'pms:imp-training:update', 3, 2, 993109180002, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0'),
(993109180005, '培训记录删除', 'pms:imp-training:delete', 3, 3, 993109180002, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0'),
(993109180006, '培训记录外发', 'pms:imp-training:issue', 3, 4, 993109180002, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p001_v303', NOW(), 'p001_v303', NOW(), b'0');

-- 已可见交付件归集菜单（19018）的角色同步可见培训页与按钮，幂等
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT source.role_id, m.menu_id, 'p001_v303', NOW(), 'p001_v303', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN (SELECT 993109180002 AS menu_id
      UNION SELECT 993109180003 UNION SELECT 993109180004
      UNION SELECT 993109180005 UNION SELECT 993109180006) m
WHERE source.`menu_id` = 19018 AND source.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.`tenant_id` = source.`tenant_id`
                    AND existing.`role_id` = source.`role_id`
                    AND existing.`menu_id` = m.menu_id AND existing.`deleted` = b'0');
