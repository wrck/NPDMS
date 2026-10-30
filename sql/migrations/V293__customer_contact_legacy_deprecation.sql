-- V293: pms_customer / pms_customer_contact 旧链退役标记与菜单收口（F-CUS-001 旧实现前向迁移一次性退出）
-- 数据合并已由 V106（pms_customer -> cus_customer_master）与 V216（pms_customer_contact -> cus_customer_contact）
-- 完成（NOT EXISTS 幂等前向迁移，来源行全量覆盖）；后端 /pms/customer、/pms/customer-contact 旧链
-- Controller/Service/Mapper/DO 与前端旧 api/页面已随本次退役删除，权限串 pms:customer:* /
-- pms:customer-contact:* 由 pms-module-customer 新链（/pms/customers、/api/v1/pms/customer-contacts）沿用。
-- 本迁移不删除旧表数据：保留为 V106/V216 前向迁移来源证据，仅追加可读废弃标记（对齐 V287 口径）。

-- 1. 旧表废弃标记（历史数据保留为迁移来源证据，待清理）
ALTER TABLE `pms_customer`
  COMMENT = '[DEPRECATED 2026-09-18] pms_customer 旧链客户主档，已由 cus_customer_master 承接（V106 前向迁移），仅保留历史数据作为来源证据，待清理';
ALTER TABLE `pms_customer_contact`
  COMMENT = '[DEPRECATED 2026-09-18] pms_customer_contact 旧链客户联系人，已由 cus_customer_contact 承接（V216 前向迁移），仅保留历史数据作为来源证据，待清理';

-- 2. 旧“客户历史（只读）”菜单及其按钮退役（页面已删除；权限串由 198760 工作台按钮族沿用）
UPDATE `system_menu`
SET `deleted` = b'1', `visible` = b'0', `status` = 1, `updater` = 'migration', `update_time` = NOW()
WHERE `id` IN (18001, 18002, 18003, 18004) AND `deleted` = b'0';

UPDATE `system_role_menu`
SET `deleted` = b'1', `updater` = 'migration', `update_time` = NOW()
WHERE `menu_id` IN (18001, 18002, 18003, 18004) AND `deleted` = b'0';

-- 3. 联系人权限按钮从旧菜单 18001 迁挂到新联系人工作台 18010（原挂旧菜单，删除旧菜单会连带失效）
UPDATE `system_menu`
SET `parent_id` = 18010, `updater` = 'migration', `update_time` = NOW()
WHERE `id` IN (18005, 18006, 18007, 18008)
  AND `parent_id` = 18001
  AND `deleted` = b'0';
