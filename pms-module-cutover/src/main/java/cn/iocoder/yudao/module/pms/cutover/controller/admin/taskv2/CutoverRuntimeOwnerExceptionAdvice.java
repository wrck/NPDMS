package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 新工作台的跨 Owner 失败保留原因；不把不可用或范围失效展示成操作成功。 */
@RestControllerAdvice(assignableTypes = {CutoverRuntimeTaskController.class, CutoverRuntimeChecklistController.class,
        CutoverRuntimePlanController.class, CutoverRuntimeApprovalController.class, CutoverRuntimeClosureController.class,
        CutoverContactChoicesController.class, CutoverResultProgressController.class})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class CutoverRuntimeOwnerExceptionAdvice {
    @ExceptionHandler(CutoverOwnerFactException.class)
    public ResponseEntity<CommonResult<?>> owner(CutoverOwnerFactException failure) {
        int status = switch (failure.code()) {
            case DATA_SCOPE_FORBIDDEN -> 403;
            case STALE -> 409;
            case PROVIDER_UNAVAILABLE -> 503;
            case INVALID_FACT -> 422;
        };
        return error(status, failure.getMessage());
    }

    @ExceptionHandler(ImplementationReadinessException.class)
    public ResponseEntity<CommonResult<?>> readiness(ImplementationReadinessException failure) {
        int status = switch (failure.getCode()) {
            case TENANT_CONTEXT_MISMATCH -> 403;
            case SNAPSHOT_NOT_FOUND -> 422;
            case PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED -> 503;
            case INVALID_REQUEST, DUPLICATE_DEVICE -> 409;
        };
        return error(status, failure.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<CommonResult<?>> forbidden(SecurityException failure) { return error(403, failure.getMessage()); }

    private ResponseEntity<CommonResult<?>> error(int status, String message) {
        return ResponseEntity.status(status).body(CommonResult.error(1_011_005_200 + status, message));
    }
}
