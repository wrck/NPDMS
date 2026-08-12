-- =====================================================================
-- V32 清理 yudao 上游推广与外链数据
-- 范围：system_menu 外链菜单、system_tenant 租户推广、system_users 用户推广、
--       system_oauth2_client 客户端外链、infra_file_config 示例域名、system_notice 公告图片
-- 原则：不破坏登录与业务逻辑，仅去除 iocoder.cn / zsxq.com 等推广外链
-- =====================================================================

-- ---------------- 1. system_menu：删除 3 个芋道外链菜单 ----------------
-- id=1254 作者动态 (https://www.iocoder.cn)
-- id=2159 Boot 开发文档 (https://doc.iocoder.cn/)
-- id=2160 Cloud 开发文档 (https://cloud.iocoder.cn)
DELETE FROM `system_menu` WHERE `id` IN (1254, 2159, 2160);

-- ---------------- 2. system_tenant：清理租户推广信息 ----------------
-- id=1 主租户：名称“芋道源码”→“NPMS默认租户”，websites 去除 iocoder.cn
UPDATE `system_tenant`
SET `name` = 'NPMS默认租户',
    `contact_name` = '管理员',
    `websites` = '127.0.0.1:18081'
WHERE `id` = 1;

-- id=121 小租户：websites 去除 zsxq.iocoder.cn
UPDATE `system_tenant`
SET `websites` = '127.0.0.1:18081'
WHERE `id` = 121;

-- id=122 测试租户：联系人“芋道”→“测试”，websites 去除 test.iocoder.cn
UPDATE `system_tenant`
SET `contact_name` = '测试',
    `websites` = '127.0.0.1:18081'
WHERE `id` = 122;

-- ---------------- 3. system_users：清理用户推广信息 ----------------
-- id=1 admin：昵称“芋道源码”→“管理员”，头像清空
UPDATE `system_users`
SET `nickname` = '管理员',
    `avatar` = '',
    `email` = 'admin@npms.local'
WHERE `id` = 1;

-- id=100 yudao：昵称“芋道”→“测试用户”，邮箱去推广
UPDATE `system_users`
SET `nickname` = '测试用户',
    `email` = 'yudao@npms.local'
WHERE `id` = 100;

-- id=103 yuanma：昵称“源码”→“研发”，邮箱去推广
UPDATE `system_users`
SET `nickname` = '研发',
    `email` = 'yuanma@npms.local'
WHERE `id` = 103;

-- ---------------- 4. system_oauth2_client：清理客户端外链 ----------------
-- id=1 default：名称“芋道源码”→“NPMS默认应用”，logo 清空，redirect_uris 去推广
UPDATE `system_oauth2_client`
SET `name` = 'NPMS默认应用',
    `logo` = '',
    `redirect_uris` = '["http://127.0.0.1:18081"]'
WHERE `id` = 1;

-- id=40 test：logo 清空，redirect_uris 去推广
UPDATE `system_oauth2_client`
SET `logo` = '',
    `redirect_uris` = '["http://127.0.0.1:18081"]'
WHERE `id` = 40;

-- id=41 yudao-sso-demo-by-code：logo 清空
UPDATE `system_oauth2_client`
SET `logo` = ''
WHERE `id` = 41;

-- id=42 yudao-sso-demo-by-password：logo 清空
UPDATE `system_oauth2_client`
SET `logo` = ''
WHERE `id` = 42;

-- ---------------- 5. infra_file_config：清理示例存储域名 ----------------
-- id=22 七牛存储器：domain 去推广
UPDATE `infra_file_config`
SET `config` = JSON_SET(`config`, '$.domain', 'http://example.com')
WHERE `id` = 22;

-- id=24 腾讯云存储：domain 去推广
UPDATE `infra_file_config`
SET `config` = JSON_SET(`config`, '$.domain', 'http://example.com')
WHERE `id` = 24;

-- id=25 阿里云存储：domain 去推广
UPDATE `infra_file_config`
SET `config` = JSON_SET(`config`, '$.domain', 'http://example.com')
WHERE `id` = 25;

-- ---------------- 6. system_notice：清理公告中的芋道图片外链 ----------------
-- id=2 维护通知：内容含 test.yudao.iocoder.cn 图片，清空图片标签
UPDATE `system_notice`
SET `content` = '<p>维护通知：2018-07-01 系统凌晨维护</p>'
WHERE `id` = 2;
