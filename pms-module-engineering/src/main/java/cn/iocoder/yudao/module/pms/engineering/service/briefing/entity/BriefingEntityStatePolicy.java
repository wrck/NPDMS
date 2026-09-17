package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

/** 从旧交底服务复制的状态规则；不新增业务状态、审核节点或完成条件。 */
public final class BriefingEntityStatePolicy {
    public static final int DRAFT = 0;
    public static final int GENERATED = 1;
    public static final int AUDITED = 2;
    public static final int PUBLISHED = 3;
    public static final int TERMINATED = 4;

    private BriefingEntityStatePolicy() {}

    public static boolean isDraft(Integer status) { return status != null && status == DRAFT; }
    public static boolean canGenerate(Integer status) { return isDraft(status); }
    public static boolean canApprove(Integer status) { return status != null && status == GENERATED; }
    public static boolean canPublish(Integer status) { return status != null && status == AUDITED; }
    public static boolean canTerminate(Integer status) {
        return status != null && status >= DRAFT && status <= AUDITED;
    }
    public static int approvalTarget(String action) {
        if ("PASS".equals(action)) return AUDITED;
        if ("REJECT".equals(action)) return DRAFT;
        throw new IllegalArgumentException("审核动作必须为 PASS 或 REJECT");
    }
}
