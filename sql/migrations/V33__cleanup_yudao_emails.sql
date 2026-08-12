-- =====================================================================
-- V33 清理 yudao 上游邮箱示例与残留推广数据
-- 范围：system_users 邮箱、system_tenant 联系方式、infra_test 数据清理
-- =====================================================================

-- ---------------- 1. system_users：清理芋道邮箱 ----------------
-- id=1 admin：邮箱 admin@npms.local（V32已设置）
-- id=100 yudao：邮箱 yudao@npms.local（V32已设置）
-- id=103 yuanma：邮箱 yuanma@npms.local（V32已设置）

-- 清理其他可能存在的 iocoder.cn 邮箱
UPDATE `system_users`
SET `email` = REPLACE(`email`, '@iocoder.cn', '@npms.local')
WHERE `email` LIKE '%@iocoder.cn';

-- 清理 13aoteman@126.com 等推广邮箱
UPDATE `system_users`
SET `email` = ''
WHERE `email` = '13aoteman@126.com';

-- ---------------- 2. system_tenant：清理联系方式邮箱 ----------------
UPDATE `system_tenant`
SET `contact_name` = '管理员'
WHERE `contact_name` LIKE '%芋%';

-- ---------------- 3. system_mail_account：清理示例邮箱账号 ----------------
-- 清理邮件账号中的 iocoder.cn 相关配置
UPDATE `system_mail_account`
SET `username` = REPLACE(`username`, '@iocoder.cn', '@npms.local')
WHERE `username` LIKE '%@iocoder.cn';

-- ---------------- 4. system_social_user：清理三方用户头像外链 ----------------
UPDATE `system_social_user`
SET `avatar` = ''
WHERE `avatar` LIKE '%iocoder.cn%';

-- ---------------- 5. system_oauth2_client：清理 redirect_uris 中的 iocoder.cn ----------------
UPDATE `system_oauth2_client`
SET `redirect_uris` = REPLACE(`redirect_uris`, 'iocoder.cn', 'example.com')
WHERE `redirect_uris` LIKE '%iocoder.cn%';

UPDATE `system_oauth2_client`
SET `logo` = ''
WHERE `logo` LIKE '%iocoder.cn%';

-- ---------------- 6. infra_file_config：清理所有 iocoder.cn 域名 ----------------
UPDATE `infra_file_config`
SET `config` = REPLACE(`config`, 'iocoder.cn', 'example.com')
WHERE `config` LIKE '%iocoder.cn%';

-- ---------------- 7. infra_file：清理文件记录中的 iocoder.cn URL ----------------
UPDATE `infra_file`
SET `url` = REPLACE(`url`, 'iocoder.cn', 'example.com')
WHERE `url` LIKE '%iocoder.cn%';

-- ---------------- 8. system_notice：清理公告内容中的 iocoder.cn ----------------
UPDATE `system_notice`
SET `content` = ''
WHERE `content` LIKE '%iocoder.cn%';

-- ---------------- 9. system_dict_data：清理字典数据中的 iocoder.cn ----------------
UPDATE `system_dict_data`
SET `label` = REPLACE(`label`, 'iocoder.cn', 'example.com'),
    `value` = REPLACE(`value`, 'iocoder.cn', 'example.com')
WHERE `label` LIKE '%iocoder.cn%' OR `value` LIKE '%iocoder.cn%';
