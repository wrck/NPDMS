package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

/** 七路由请求边界错误；区分请求结构与Header，不依赖异常消息分类。 */
final class CutoverPlanRequestException extends IllegalArgumentException {
    enum Reason { REQUEST_SCHEMA_INVALID, HEADER_REQUIRED_OR_INVALID }

    private final Reason reason;

    CutoverPlanRequestException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    Reason reason() { return reason; }
}
