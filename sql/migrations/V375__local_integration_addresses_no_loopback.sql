-- =====================================================================
-- V375 集成地址去回环化：本地环境统一使用宿主机局域网地址
-- 背景：前后端与集成地址要求全部使用本机局域网 IP（10.210.0.11），
--       不得使用 127.0.0.1/localhost 回环地址
-- 范围：system_tenant 租户站点、system_oauth2_client 回调白名单、
--       infra_file_config 文件访问域名/端点、system_mail_account SMTP 主机
-- 原则：仅替换种子数据中的回环地址字面量，不改端口与业务语义；
--       不触碰 system_users.login_ip 等历史审计字段；
--       REPLACE 幂等，重复执行无副作用
-- =====================================================================

-- ---------------- 1. system_tenant：租户站点域名 ----------------
UPDATE `system_tenant`
SET `websites` = REPLACE(`websites`, '127.0.0.1', '10.210.0.11')
WHERE `websites` LIKE '%127.0.0.1%';

-- ---------------- 2. system_oauth2_client：OAuth2 回调白名单 ----------------
UPDATE `system_oauth2_client`
SET `redirect_uris` = REPLACE(`redirect_uris`, '127.0.0.1', '10.210.0.11')
WHERE `redirect_uris` LIKE '%127.0.0.1%';

-- ---------------- 3. infra_file_config：文件存储访问域名/端点 ----------------
UPDATE `infra_file_config`
SET `config` = REPLACE(`config`, '127.0.0.1', '10.210.0.11')
WHERE `config` LIKE '%127.0.0.1%';

-- ---------------- 4. system_mail_account：SMTP 主机 ----------------
UPDATE `system_mail_account`
SET `host` = REPLACE(`host`, '127.0.0.1', '10.210.0.11')
WHERE `host` LIKE '%127.0.0.1%';
