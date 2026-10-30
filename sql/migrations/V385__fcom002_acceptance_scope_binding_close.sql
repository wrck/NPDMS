-- V385: acc_acceptance_scope_binding 允许区间终结（Q-FCOM-002 2026-09-30 裁决）。
--
-- 背景：需求方裁决"退出或回退验收阶段即关闭既有 AcceptanceScopeBinding 并解锁
-- （落 effective_to 至回退时点、解除资源绑定锁定）"。V160 发布的
-- chk_acceptance_scope_effective 把 effective_to 钉死为 NULL，表只能表达活跃锁，
-- 关闭语义（生效区间结束）无法落库。
-- 本迁移仅放开该约束：binding_status 仍恒为 LOCKED（chk_acceptance_scope_status
-- 保留，关闭不引入新状态值），活跃判定 = LOCKED + effective_to IS NULL 不变。
-- 既有数据 effective_to 全为 NULL，不受影响。
ALTER TABLE `acc_acceptance_scope_binding`
    DROP CHECK `chk_acceptance_scope_effective`;
