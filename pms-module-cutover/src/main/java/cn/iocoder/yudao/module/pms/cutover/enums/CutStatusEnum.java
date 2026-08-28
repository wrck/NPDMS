package cn.iocoder.yudao.module.pms.cutover.enums;

/**
 * 割接域状态枚举常量，集中维护便于 Service/Controller/前端字典对齐。
 */
public interface CutStatusEnum {

    // 当前实现仅保留P6之前的兼容状态；完整P1～P6状态机由后续Feature重建。
    Integer CUT_TASK_DRAFT = 0;
    Integer CUT_TASK_PREPARING = 1;
    Integer CUT_TASK_PENDING_REVIEW = 2;
    Integer CUT_TASK_CLOSURE_IN_PROGRESS = 3;

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

}
