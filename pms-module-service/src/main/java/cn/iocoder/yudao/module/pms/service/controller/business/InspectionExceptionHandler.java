package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.pms.service.controller.business")
public class InspectionExceptionHandler {
    public record ErrorData(String reasonCode, Long currentVersion, boolean retryable) { }

    @ExceptionHandler(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException.class)
    public ResponseEntity<CommonResult<ErrorData>> handleCollection(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException error) {
        return handle(new InspectionException(InspectionException.Reason.INVALID_REQUEST, error.getMessage()));
    }

    @ExceptionHandler(InspectionException.class)
    public ResponseEntity<CommonResult<ErrorData>> handle(InspectionException error) {
        int status = switch (error.getReason()) {
            case NOT_FOUND -> 404;
            case FORBIDDEN -> 403;
            case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, IN_PROGRESS, PRECHECK_REQUIRED -> 409;
            case DEPENDENCY_UNAVAILABLE -> 503;
            default -> 400;
        };
        CommonResult<ErrorData> result = CommonResult.error(1_013_007_000 + error.getReason().ordinal(), error.getMessage());
        result.setData(new ErrorData(error.getReason().name(), error.getCurrentVersion(),
                error.getReason() == InspectionException.Reason.IN_PROGRESS
                        || error.getReason() == InspectionException.Reason.DEPENDENCY_UNAVAILABLE));
        return ResponseEntity.status(status).body(result);
    }
}
