package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.business.MaterialExchangeBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.materialexchange.business.MaterialExchangeBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/material-exchange-business")
public class MaterialExchangeBusinessController extends ProjectBusinessController<MaterialExchangeBusinessService,MaterialExchangeBusinessDO> {
    public record Command(@NotNull Long version,@NotBlank String idempotencyKey,String action,@Size(max=1000) String opinion){}
    @PostMapping("/{id}/submit") public CommonResult<BusinessOperationReceipt> submit(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.submit(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/withdraw") public CommonResult<BusinessOperationReceipt> withdraw(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.withdraw(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/terminate") public CommonResult<BusinessOperationReceipt> terminate(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.terminate(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/approve") public CommonResult<BusinessOperationReceipt> approve(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.approve(id,command.version(),command.idempotencyKey(),command.action(),command.opinion()));}
}
