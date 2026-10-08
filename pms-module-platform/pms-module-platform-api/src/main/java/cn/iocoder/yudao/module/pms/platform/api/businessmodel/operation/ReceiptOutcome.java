package cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation;

/**
 * 操作回执结果：保存成功、异步受理、审批通过、业务生效分别表达；
 * 异步受理不被解释为业务完成，FAILED 必须给出原因并支持查询回执恢复。
 */
public enum ReceiptOutcome {
    SAVED, DELETED, ACCEPTED, APPROVAL_PENDING, EFFECTED, FAILED
}
