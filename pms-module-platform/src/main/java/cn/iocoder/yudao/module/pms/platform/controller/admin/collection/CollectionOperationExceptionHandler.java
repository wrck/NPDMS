package cn.iocoder.yudao.module.pms.platform.controller.admin.collection;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.COLLECTION_OPERATION_REJECTED;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CollectionOperationExceptionHandler {
    @ExceptionHandler(CollectionOperationException.class)
    public CommonResult<?> rejected(CollectionOperationException failure) {
        return CommonResult.error(COLLECTION_OPERATION_REJECTED.getCode(), failure.getMessage());
    }
}
