package cn.iocoder.yudao.module.pms.service.enums;

/**
 * 巡检维保域状态枚举常量，集中维护便于 Service/Controller/前端字典对齐。
 */
public interface SrvStatusEnum {

    // 巡检任务状态：0草稿 1待执行 2执行中 3待确认 4已完成 5已取消
    Integer SRV_TASK_DRAFT = 0;
    Integer SRV_TASK_PENDING = 1;
    Integer SRV_TASK_EXECUTING = 2;
    Integer SRV_TASK_PENDING_CONFIRM = 3;
    Integer SRV_TASK_COMPLETED = 4;
    Integer SRV_TASK_CANCELLED = 5;

    // 巡检规则状态：0草稿 1已发布 2已停用
    Integer SRV_RULE_DRAFT = 0;
    Integer SRV_RULE_PUBLISHED = 1;
    Integer SRV_RULE_DISABLED = 2;

    // 在线巡检执行状态：0待执行 1执行中 2已完成 3异常
    Integer SRV_EXECUTION_PENDING = 0;
    Integer SRV_EXECUTION_IN_PROGRESS = 1;
    Integer SRV_EXECUTION_COMPLETED = 2;
    Integer SRV_EXECUTION_ABNORMAL = 3;

    // 离线文件解析状态：0待解析 1解析中 2解析成功 3解析失败
    Integer SRV_OFFLINE_PENDING = 0;
    Integer SRV_OFFLINE_PARSING = 1;
    Integer SRV_OFFLINE_PARSED = 2;
    Integer SRV_OFFLINE_FAILED = 3;

    // 巡检报告状态：0草稿 1已生成 2已归档
    Integer SRV_REPORT_DRAFT = 0;
    Integer SRV_REPORT_GENERATED = 1;
    Integer SRV_REPORT_ARCHIVED = 2;

    // 巡检问题状态：0待分派 1已分派 2待验证 3已关闭 4已取消
    Integer SRV_ISSUE_OPEN = 0;
    Integer SRV_ISSUE_ASSIGNED = 1;
    Integer SRV_ISSUE_PENDING_VERIFY = 2;
    Integer SRV_ISSUE_CLOSED = 3;
    Integer SRV_ISSUE_CANCELLED = 4;

    // 维保状态：0未生效 1生效中 2即将过期 3已过期 4已续保
    Integer SRV_MAINT_NOT_EFFECTIVE = 0;
    Integer SRV_MAINT_EFFECTIVE = 1;
    Integer SRV_MAINT_EXPIRING = 2;
    Integer SRV_MAINT_EXPIRED = 3;
    Integer SRV_MAINT_RENEWED = 4;
}
