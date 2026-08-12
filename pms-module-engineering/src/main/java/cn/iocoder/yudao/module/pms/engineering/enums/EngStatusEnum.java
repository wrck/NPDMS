package cn.iocoder.yudao.module.pms.engineering.enums;

/**
 * 工程实施域状态枚举常量，集中维护便于 Service/Controller/前端字典对齐。
 */
public interface EngStatusEnum {

    // 工勘状态：0草稿 1已确认 2已驳回 3已归档
    Integer SITE_SURVEY_DRAFT = 0;
    Integer SITE_SURVEY_CONFIRMED = 1;
    Integer SITE_SURVEY_REJECTED = 2;
    Integer SITE_SURVEY_ARCHIVED = 3;

    // 需求状态：0草稿 1已提交 2已生效 3已归档
    Integer REQUIREMENT_DRAFT = 0;
    Integer REQUIREMENT_SUBMITTED = 1;
    Integer REQUIREMENT_EFFECTIVE = 2;
    Integer REQUIREMENT_ARCHIVED = 3;

    // 方案状态：0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止
    Integer SOLUTION_DRAFT = 0;
    Integer SOLUTION_SUBMITTED = 1;
    Integer SOLUTION_IN_REVIEW = 2;
    Integer SOLUTION_APPROVED = 3;
    Integer SOLUTION_REJECTED = 4;
    Integer SOLUTION_WITHDRAWN = 5;
    Integer SOLUTION_TERMINATED = 6;

    // 资源就绪状态：0未就绪 1已就绪 2异常
    Integer RESOURCE_NOT_READY = 0;
    Integer RESOURCE_READY = 1;
    Integer RESOURCE_ABNORMAL = 2;

    // 到货签收状态：0待签收 1已签收 2异常
    Integer ARRIVAL_PENDING = 0;
    Integer ARRIVAL_SIGNED = 1;
    Integer ARRIVAL_ABNORMAL = 2;

    // 硬件安装状态：0待安装 1进行中 2已完成 3异常
    Integer INSTALLATION_PENDING = 0;
    Integer INSTALLATION_IN_PROGRESS = 1;
    Integer INSTALLATION_COMPLETED = 2;
    Integer INSTALLATION_ABNORMAL = 3;

    // 配置调试状态：0待调试 1进行中 2已完成 3异常
    Integer CONFIGURATION_PENDING = 0;
    Integer CONFIGURATION_IN_PROGRESS = 1;
    Integer CONFIGURATION_COMPLETED = 2;
    Integer CONFIGURATION_ABNORMAL = 3;

    // 业务联调状态：0待联调 1进行中 2通过 3失败
    Integer JOINT_TEST_PENDING = 0;
    Integer JOINT_TEST_IN_PROGRESS = 1;
    Integer JOINT_TEST_PASSED = 2;
    Integer JOINT_TEST_FAILED = 3;

    // 实施问题状态：0待处理 1整改中 2待验证 3已关闭 4已挂起
    Integer ISSUE_OPEN = 0;
    Integer ISSUE_RECTIFYING = 1;
    Integer ISSUE_PENDING_VERIFICATION = 2;
    Integer ISSUE_CLOSED = 3;
    Integer ISSUE_SUSPENDED = 4;

    // 交付件状态：0待归集 1已归集 2已作废
    Integer DELIVERABLE_PENDING = 0;
    Integer DELIVERABLE_ARCHIVED = 1;
    Integer DELIVERABLE_VOID = 2;
}
