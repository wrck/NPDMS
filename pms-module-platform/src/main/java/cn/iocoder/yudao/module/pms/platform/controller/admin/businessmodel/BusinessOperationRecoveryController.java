package cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** One receipt recovery endpoint for every default/controlled thin business service. */
@RestController
@RequestMapping("/api/v1/pms/business-models")
@RequiredArgsConstructor
@Validated
public class BusinessOperationRecoveryController {
    private final BusinessOperationDispatcher dispatcher;
    @GetMapping("/{ownerModule}/{entityType}/operations/{operationCode}/receipt")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    @Transactional
    public CommonResult<BusinessOperationReceipt> recover(@PathVariable String ownerModule,@PathVariable String entityType,
            @PathVariable String operationCode,@RequestParam @Positive int operationVersion,
            @RequestParam @NotBlank @Size(max=128) String idempotencyKey) {
        return success(dispatcher.recoverReceipt(ownerModule,entityType,operationCode,operationVersion,idempotencyKey));
    }
}
