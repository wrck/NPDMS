-- Project detail remediation: retain the exact calculation inputs for every plan version.
ALTER TABLE sol_stage_plan_batch
    ADD COLUMN calculated_start DATE NULL COMMENT '本版本计算开始日期',
    ADD COLUMN calculated_end DATE NULL COMMENT '本版本计算结束日期',
    ADD COLUMN task_plans_json LONGTEXT NULL COMMENT '本版本阶段内任务计划快照',
    ADD COLUMN input_snapshot LONGTEXT NULL COMMENT '冻结计划及回款节点计划验收时间输入快照';
