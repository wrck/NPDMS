package cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation;

/** 回执携带的真实引用：文件、审批、专业结果或命令结果。 */
public record ResultReference(Kind kind, String ownerModule, String value) {

    public enum Kind { FILE, APPROVAL, RESULT, COMMAND }
}
