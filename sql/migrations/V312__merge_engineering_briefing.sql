-- ============================================================================
-- V312: 合并工程交底双实现，统一保留 sol_eng_briefing 承载
--
-- 背景：V251 曾并行复制"工程交底（独立实体）"承载（sol_engineering_briefing 表 +
-- BriefingEntity* 前后端 + 菜单 993109170001），与原工程交底实现（sol_eng_briefing
-- 表 + Briefing* + 菜单 19196）业务等价：字段同构、状态机相同（0草稿/1已生成/
-- 2已审核/3已发布/4已作废）、权限串同为 pms:sol-briefing:*，其 import-legacy 仅是
-- 从 sol_eng_briefing 向副本表的反向搬运。需求方确认两者为同一业务并要求按其他
-- sol_ 实体命名统一保留 sol_eng_briefing。
--
-- 本迁移（配套代码删除见同分支工程提交）：
--   1) sol_engineering_briefing 更名 sol_engineering_briefing_retired 冻结留痕；
--      其中仅 1 条 2026-09-20 验证草稿（code=sxxs, tenant 1），不搬入正表；
--   2) 停用"工程交底（独立实体）"菜单 993109170001（status=1，与 V311 同口径），
--      关联 system_role_menu 行保留不清理，菜单停用即不可达。
-- 原工程交底（菜单 19196 / /pms/sol-briefing / sol_eng_briefing）不受影响。
-- ============================================================================

RENAME TABLE `sol_engineering_briefing` TO `sol_engineering_briefing_retired`;

UPDATE system_menu
SET status = 1, updater = 'briefing-entity-merge', update_time = NOW()
WHERE id = 993109170001
  AND deleted = 0;
