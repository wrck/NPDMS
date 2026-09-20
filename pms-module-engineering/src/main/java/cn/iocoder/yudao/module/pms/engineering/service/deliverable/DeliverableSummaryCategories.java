package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

/**
 * 按项目交付件汇总类别（6.4 Demo 6 类 + 其他工程归集）。
 */
public final class DeliverableSummaryCategories {

    /** 到货签收单（5.1，自动归档在途：EXE-01 切片承接中） */
    public static final String RECEIPT = "RECEIPT";
    /** 实施方案（4.1 审批通过自动归档） */
    public static final String SCHEME = "SCHEME";
    /** 初验报告（6.3 同步） */
    public static final String PRELIMINARY = "PRELIMINARY";
    /** 终验报告（6.3 同步） */
    public static final String FINAL = "FINAL";
    /** 现场培训记录（6.1，整页承接中） */
    public static final String TRAINING = "TRAINING";
    /** 满意度调查报告（6.2 同步） */
    public static final String SATISFACTION = "SATISFACTION";
    /** 其他工程归集（日报/服务单/测试记录/配置档案/完工证明等） */
    public static final String OTHER = "OTHER";
    /** ACC 归档补偿中状态 */
    public static final String PENDING_ARCHIVE = "PENDING_COMPENSATION";

    private DeliverableSummaryCategories() {
    }
}
