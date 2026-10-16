package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.business.ArrivalBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.arrival.business.ArrivalBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pms/arrival-business")
public class ArrivalBusinessController extends ProjectBusinessController<ArrivalBusinessService, ArrivalBusinessDO> {
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank String idempotencyKey) { }
    @PostMapping("/{id}/sign")
    public CommonResult<BusinessOperationReceipt> sign(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.sign(id, action.version(), action.idempotencyKey()));
    }
    @PostMapping("/{id}/abnormal")
    public CommonResult<BusinessOperationReceipt> abnormal(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.abnormal(id, action.version(), action.idempotencyKey()));
    }
}
