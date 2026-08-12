package cn.iocoder.yudao.module.pms.cutover.enums;

/**
 * 割接域状态枚举常量，集中维护便于 Service/Controller/前端字典对齐。
 */
public interface CutStatusEnum {

    // 割接任务状态：0草稿 1准备中 2待评审 3待执行 4执行中 5稳定观察 6已完成 7已回退 8已终止
    Integer CUT_TASK_DRAFT = 0;
    Integer CUT_TASK_PREPARING = 1;
    Integer CUT_TASK_PENDING_REVIEW = 2;
    Integer CUT_TASK_PENDING_EXECUTION = 3;
    Integer CUT_TASK_EXECUTING = 4;
    Integer CUT_TASK_OBSERVING = 5;
    Integer CUT_TASK_COMPLETED = 6;
    Integer CUT_TASK_ROLLBACK = 7;
    Integer CUT_TASK_TERMINATED = 8;

    // 割接风险/调研状态：0待处理 1处理中 2已闭环 3已挂起
    Integer CUT_RISK_OPEN = 0;
    Integer CUT_RISK_PROCESSING = 1;
    Integer CUT_RISK_CLOSED = 2;
    Integer CUT_RISK_SUSPENDED = 3;

    // 割接方案状态：0草稿 1待评审 2已通过 3已驳回 4已终止
    Integer CUT_PLAN_DRAFT = 0;
    Integer CUT_PLAN_PENDING_REVIEW = 1;
    Integer CUT_PLAN_APPROVED = 2;
    Integer CUT_PLAN_REJECTED = 3;
    Integer CUT_PLAN_TERMINATED = 4;

    // 割接执行记录状态：0待执行 1执行中 2已通过 3失败 4已回退
    Integer CUT_EXECUTION_PENDING = 0;
    Integer CUT_EXECUTION_EXECUTING = 1;
    Integer CUT_EXECUTION_PASSED = 2;
    Integer CUT_EXECUTION_FAILED = 3;
    Integer CUT_EXECUTION_ROLLBACK = 4;

    // 稳定观察状态：0观察中 1已通过 2异常 3已归档
    Integer CUT_OBSERVATION_OBSERVING = 0;
    Integer CUT_OBSERVATION_PASSED = 1;
    Integer CUT_OBSERVATION_ABNORMAL = 2;
    Integer CUT_OBSERVATION_ARCHIVED = 3;

}
