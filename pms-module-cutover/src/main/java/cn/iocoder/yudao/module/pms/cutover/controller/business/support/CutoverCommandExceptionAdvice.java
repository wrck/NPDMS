package cn.iocoder.yudao.module.pms.cutover.controller.business.support;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.pms.cutover.controller.business")
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class CutoverCommandExceptionAdvice {
    @ExceptionHandler({IllegalArgumentException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<CutoverErrorData>> invalid(Exception failure) {
        var result = CommonResult.<CutoverErrorData>error(1_011_012_400,
                failure instanceof IllegalArgumentException ? failure.getMessage() : "请求格式或必填字段无效");
        result.setData(new CutoverErrorData("INVALID_REQUEST", "REQUEST_SCHEMA_INVALID", "FIX_REQUEST"));
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverErrorData>> forbidden(Exception failure) {
        var result = CommonResult.<CutoverErrorData>error(1_011_012_403, "无权执行该割接操作");
        result.setData(new CutoverErrorData("FORBIDDEN", "PERMISSION_DENIED", "RETURN_TO_LIST"));
        return ResponseEntity.status(403).body(result);
    }

    @ExceptionHandler(CutoverCommandException.class)
    public ResponseEntity<CommonResult<CutoverErrorData>> command(CutoverCommandException failure) {
        int status = failure.reason() == CutoverCommandException.Reason.INVALID_INTENT ? 400 : 409;
        String recovery = switch (failure.reason()) {
            case INVALID_INTENT -> "FIX_REQUEST";
            case IDEMPOTENCY_CONFLICT -> "START_NEW_INTENT";
            case IDEMPOTENCY_IN_PROGRESS -> "RETRY_SAME_KEY";
        };
        var result = CommonResult.<CutoverErrorData>error(1_011_012_000 + status, failure.getMessage());
        result.setData(new CutoverErrorData(failure.reason().name(), failure.reason().name(), recovery));
        return ResponseEntity.status(status).body(result);
    }
}
