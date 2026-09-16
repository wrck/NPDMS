package cn.iocoder.yudao.module.pms.workflow.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 结果状态码（迁移自源工程 pms-common com.dp.plat.common.result.ResultCode）。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "成功"),
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "方法不支持"),
    REQUEST_TIMEOUT(408, "请求超时"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过多"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    INTEGRATION_FAILURE(503, "集成调用失败"),
    BUSINESS_ERROR(1001, "业务异常"),
    TOKEN_INVALID(1002, "Token 无效"),
    TOKEN_EXPIRED(1003, "Token 已过期"),
    ACCOUNT_LOCKED(1004, "账号已锁定"),
    ACCOUNT_DISABLED(1005, "账号已禁用"),
    USERNAME_OR_PASSWORD_ERROR(1006, "用户名或密码错误");

    private final int code;
    private final String message;
}