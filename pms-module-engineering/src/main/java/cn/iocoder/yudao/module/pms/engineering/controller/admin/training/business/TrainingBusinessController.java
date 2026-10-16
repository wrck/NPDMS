package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.business;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.business.TrainingBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import org.springframework.web.bind.annotation.*;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController
@RequestMapping("/api/v1/pms/training-business")
public class TrainingBusinessController extends ProjectBusinessController<TrainingBusinessService,TrainingBusinessDO> {
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank String idempotencyKey) { }
    @PostMapping("/{id}/issue")
    public CommonResult<TrainingBusinessService.Issued> issue(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.issue(id, action.version(), action.idempotencyKey()));
    }
    @PostMapping("/{id}/void")
    public CommonResult<BusinessOperationReceipt> voidRecord(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.voidRecord(id, action.version(), action.idempotencyKey()));
    }
}
