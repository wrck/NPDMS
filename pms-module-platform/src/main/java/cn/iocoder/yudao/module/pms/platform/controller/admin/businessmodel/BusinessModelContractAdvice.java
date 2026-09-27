package cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一业务模型入口的契约诊断映射：并发/幂等冲突可被客户端识别为冲突而不是系统异常，
 * 授权拒绝明确返回 403；其余契约诊断返回 400 并保留定位信息。
 * 按控制器包覆盖统一入口、过程定义与执行绑定场景入口，平台不反向依赖绑定模块。
 */
@RestControllerAdvice(basePackages = {"cn.iocoder.yudao.module.pms.platform.controller.admin",
        "cn.iocoder.yudao.module.pms.bindings.controller"})
public class BusinessModelContractAdvice {

    @ExceptionHandler(BusinessContractException.class)
    public ResponseEntity<CommonResult<?>> contract(BusinessContractException exception) {
        HttpStatus status;
        CommonResult<?> body;
        switch (exception.getErrorCode()) {
            case "CONCURRENCY_CONFLICT" -> {
                status = HttpStatus.CONFLICT;
                body = CommonResult.error(ErrorCodeConstants.BUSINESS_MODEL_CONCURRENCY_CONFLICT);
            }
            case "IDEMPOTENCY_DIGEST_CONFLICT" -> {
                status = HttpStatus.CONFLICT;
                body = CommonResult.error(ErrorCodeConstants.BUSINESS_MODEL_IDEMPOTENCY_DIGEST_CONFLICT);
            }
            case "IDEMPOTENCY_IN_PROGRESS" -> {
                status = HttpStatus.CONFLICT;
                body = CommonResult.error(ErrorCodeConstants.BUSINESS_MODEL_IDEMPOTENCY_IN_PROGRESS);
            }
            case "OPERATION_VERSION_CONFLICT" -> {
                status = HttpStatus.CONFLICT;
                body = CommonResult.error(ErrorCodeConstants.BUSINESS_MODEL_OPERATION_VERSION_CONFLICT);
            }
            case "ACCESS_DENIED" -> {
                status = HttpStatus.FORBIDDEN;
                body = CommonResult.error(ErrorCodeConstants.BUSINESS_MODEL_ACCESS_DENIED.getCode(),
                        exception.getMessage());
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                body = CommonResult.error(ErrorCodeConstants.BUSINESS_MODEL_CONTRACT_REJECTED.getCode(),
                        exception.getErrorCode() + ": " + exception.getMessage());
            }
        }
        return ResponseEntity.status(status).body(body);
    }
}
