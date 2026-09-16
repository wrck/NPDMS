package cn.iocoder.yudao.module.pms.lowcode.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Lowcode 模块错误码常量。
 *
 * <p>模块段使用 1_017_000_000（1_016 段已由 commerce 模块占用）。</p>
 */
public interface ErrorCodeConstants {

    /** 低代码模块通用异常，携带动态消息（迁移自源工程 LowCodeException 的 message-only 语义）。 */
    ErrorCode LOWCODE_ERROR = new ErrorCode(1_017_000_000, "低代码模块异常");
}
