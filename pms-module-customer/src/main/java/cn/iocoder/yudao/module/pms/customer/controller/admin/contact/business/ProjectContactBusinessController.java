package cn.iocoder.yudao.module.pms.customer.controller.admin.contact.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.business.ProjectContactBusinessDO;
import cn.iocoder.yudao.module.pms.customer.service.contact.business.ProjectContactBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/api/v1/pms/project-contact-business")
public class ProjectContactBusinessController extends ProjectBusinessController<ProjectContactBusinessService,ProjectContactBusinessDO> {
    public record Delete(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,boolean confirmNoPrimary) { }
    public record Restore(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Positive Long projectId,@NotNull @PositiveOrZero Long projectVersion,@Min(0) @Max(1) int status) { }
    @PostMapping("/{id}/delete-confirmed") public CommonResult<BusinessOperationReceipt> remove(@PathVariable Long id,@Valid @RequestBody Delete request){return success(service.deleteAcknowledged(id,request.version(),request.idempotencyKey(),request.confirmNoPrimary()));}
    @PostMapping("/{id}/restore") public CommonResult<BusinessOperationReceipt> restore(@PathVariable Long id,@Valid @RequestBody Restore request){return success(service.restore(id,request.version(),request.idempotencyKey(),request.projectId(),request.projectVersion(),request.status()));}
}
