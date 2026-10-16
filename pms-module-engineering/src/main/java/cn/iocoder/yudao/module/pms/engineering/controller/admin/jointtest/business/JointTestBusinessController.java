package cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.business.JointTestBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.jointtest.business.JointTestBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pms/joint-test-business")
public class JointTestBusinessController extends ProjectBusinessController<JointTestBusinessService, JointTestBusinessDO> {
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank String idempotencyKey) { }
    @PostMapping("/{id}/start")
    public CommonResult<BusinessOperationReceipt> start(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.start(id, action.version(), action.idempotencyKey()));
    }
    @PostMapping("/{id}/pass")
    public CommonResult<BusinessOperationReceipt> pass(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.pass(id, action.version(), action.idempotencyKey()));
    }
    public record Failure(@NotNull @PositiveOrZero Long version, @NotBlank String idempotencyKey, @NotBlank String exceptionRecord) { }
    @PostMapping("/{id}/fail")
    public CommonResult<BusinessOperationReceipt> fail(@PathVariable Long id, @Valid @RequestBody Failure action) {
        return CommonResult.success(service.fail(id, action.version(), action.idempotencyKey(), action.exceptionRecord()));
    }
}
