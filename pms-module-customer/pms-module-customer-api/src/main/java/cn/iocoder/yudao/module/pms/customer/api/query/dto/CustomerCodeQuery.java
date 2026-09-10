package cn.iocoder.yudao.module.pms.customer.api.query.dto;

/** 当前租户内的精确客户编码查询；用户身份只能由调用方认证上下文提供。 */
public record CustomerCodeQuery(String code, Long actorUserId) {
}
