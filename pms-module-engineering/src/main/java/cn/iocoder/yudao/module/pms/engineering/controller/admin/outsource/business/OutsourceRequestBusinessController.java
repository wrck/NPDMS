package cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.business.OutsourceRequestBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.outsource.business.OutsourceRequestBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/outsource-business")
public class OutsourceRequestBusinessController extends ProjectBusinessController<OutsourceRequestBusinessService,OutsourceRequestBusinessDO> {
    public record Command(@NotNull Long version,@NotBlank String idempotencyKey,String action,@Size(max=1000) String opinion){}
    @PostMapping("/{id}/submit") public CommonResult<BusinessOperationReceipt> submit(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.submit(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/withdraw") public CommonResult<BusinessOperationReceipt> withdraw(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.withdraw(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/terminate") public CommonResult<BusinessOperationReceipt> terminate(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.terminate(id,command.version(),command.idempotencyKey()));}
    @PostMapping("/{id}/approve") public CommonResult<BusinessOperationReceipt> approve(@PathVariable Long id,@Valid @RequestBody Command command){return success(service.approve(id,command.version(),command.idempotencyKey(),command.action(),command.opinion()));}
    public record ContextDelete(@NotNull Long version,@NotBlank String idempotencyKey,cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection execution){}
    @PostMapping("/{id}/delete-with-context") public CommonResult<BusinessOperationReceipt> deleteWithContext(@PathVariable Long id,@Valid @RequestBody ContextDelete command){return success(service.deleteWithContext(id,command.version(),command.idempotencyKey(),command.execution()));}
}
