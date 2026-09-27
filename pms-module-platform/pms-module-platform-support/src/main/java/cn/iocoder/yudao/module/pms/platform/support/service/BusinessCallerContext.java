package cn.iocoder.yudao.module.pms.platform.support.service;

/**
 * 可信调用上下文：由运行时从服务端会话/系统契约解析租户与操作者，
 * 不接受请求自报身份。缺登录身份或系统命令契约时显式失败。
 */
public interface BusinessCallerContext {

    AbstractBusinessApplicationService.ResolvedCaller require();
}
