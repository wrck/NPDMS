package cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation;

/**
 * 操作入口类别。入口只影响准入与幂等键命名空间，不改变事实/事件链；
 * 明确受控操作缺少有效执行依据时拒绝，不能通过伪装独立入口绕过。
 */
public enum OperationEntryKind {
    INDEPENDENT, PROJECT_NODE, IMPORT, BATCH, SYSTEM, PUBLIC_LINK
}
