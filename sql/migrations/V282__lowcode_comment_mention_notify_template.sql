-- =============================================================
-- V282__lowcode_comment_mention_notify_template.sql
-- 低代码模块评论 @提及站内信模板（pms-module-lowcode 迁移种子）。
--
-- 模板参数由评论事务冻结：configType / configId / commenter / content
-- （见 pms-module-lowcode LowCodeCommentServiceImpl#sendMentionNotifications，
--  通过 yudao NotifyMessageSendApi#sendSingleMessageToAdmin 异步投递，
--  模板 code = low_comment_mention）。
-- 幂等：code 已存在（deleted=0）时跳过，重复执行安全。
-- =============================================================

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark,
     creator, create_time, updater, update_time, deleted)
SELECT
    '低代码评论@提及通知',
    'low_comment_mention',
    '低代码平台',
    '您在配置 {configType}#{configId} 中被 {commenter} @提及：{content}',
    2,
    '["configType","configId","commenter","content"]',
    0,
    'pms-module-lowcode 迁移：评论 @提及站内信模板',
    'lowcode-migrate', CURRENT_TIMESTAMP, 'lowcode-migrate', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_notify_template
    WHERE code = 'low_comment_mention'
      AND deleted = b'0'
);
