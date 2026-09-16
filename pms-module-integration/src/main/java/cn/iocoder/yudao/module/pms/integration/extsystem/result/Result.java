package cn.iocoder.yudao.module.pms.integration.extsystem.result;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.ResultCode;

import java.io.Serializable;

/**
 * 统一返回结果（迁移自源工程 com.dp.plat.common.result.Result）。
 *
 * <p>业务代码保持 {@code Result.ok(data)} / {@code Result.fail(msg)} 风格，
 * 成功码为 0（yudao 标准），错误码 500（yudao INTERNAL_SERVER_ERROR）。</p>
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return this.code == 0;
    }

    // === 成功 ===

    public static Result<Void> ok() {
        return new Result<>(0, "", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(0, message, data);
    }

    // === 失败 ===

    public static <T> Result<T> fail(String message) {
        return new Result<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }
}