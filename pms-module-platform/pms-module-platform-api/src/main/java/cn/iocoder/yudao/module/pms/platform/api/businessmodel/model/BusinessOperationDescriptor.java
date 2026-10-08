package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/**
 * 目录中主动开放的业务操作。没有业务依据的删除、批准、完成和生效动作不自动开放；
 * 冻结的操作版本由调用请求携带，重复定义同一编码直接报告冲突。
 */
public record BusinessOperationDescriptor(
        String code,
        int version,
        String name,
        StandardOperationKind kind,
        String authorizationPolicyRef) {

    public BusinessOperationDescriptor(String code, int version, String name, StandardOperationKind kind) {
        this(code, version, name, kind, null);
    }

    public enum StandardOperationKind {
        CREATE, UPDATE, DELETE, CONFIGURE, DOMAIN_COMMAND, SUBMIT, WITHDRAW, ARCHIVE
    }
}
