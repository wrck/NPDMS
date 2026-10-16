package cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.business.ExternalProcurementBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.externalprocurement.business.ExternalProcurementBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/external-procurement-business")
public class ExternalProcurementBusinessController extends ProjectBusinessController<ExternalProcurementBusinessService,ExternalProcurementBusinessDO> {
    public record Command(@NotNull Long version,@NotBlank String idempotencyKey,String action,@Size(max=1000) String opinion){}
    @PostMapping("/{id}/submit") public CommonResult<BusinessOperationReceipt> submit(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.submit(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/withdraw") public CommonResult<BusinessOperationReceipt> withdraw(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.withdraw(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/terminate") public CommonResult<BusinessOperationReceipt> terminate(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.terminate(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/approve") public CommonResult<BusinessOperationReceipt> approve(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.approve(id,command.version(),command.idempotencyKey(),command.action(),command.opinion()));}
}
