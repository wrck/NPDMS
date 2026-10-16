package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.business.ConfigurationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.configuration.business.ConfigurationBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pms/configuration-business")
public class ConfigurationBusinessController extends ProjectBusinessController<ConfigurationBusinessService, ConfigurationBusinessDO> {
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank String idempotencyKey) { }
    @PostMapping("/{id}/start")
    public CommonResult<BusinessOperationReceipt> start(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.start(id, action.version(), action.idempotencyKey()));
    }
    @PostMapping("/{id}/complete")
    public CommonResult<BusinessOperationReceipt> complete(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.complete(id, action.version(), action.idempotencyKey()));
    }
    @PostMapping("/{id}/abnormal")
    public CommonResult<BusinessOperationReceipt> abnormal(@PathVariable Long id, @Valid @RequestBody Action action) {
        return CommonResult.success(service.abnormal(id, action.version(), action.idempotencyKey()));
    }
}
