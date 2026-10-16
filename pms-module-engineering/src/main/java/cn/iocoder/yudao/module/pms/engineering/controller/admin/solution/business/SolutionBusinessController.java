package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.business;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.business.SolutionBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.solution.business.SolutionBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/solution-business")
public class SolutionBusinessController extends ProjectBusinessController<SolutionBusinessService,SolutionBusinessDO> {
    public record Command(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey){ }
    public record Review(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotBlank String processDefinitionId){ }
    public record Files(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotEmpty @Size(max=5) List<@Positive Long> referenceIds){ }
    @PostMapping("/{id}/review-submit") public CommonResult<BusinessOperationReceipt> submit(@PathVariable Long id,@Valid @RequestBody Review request){return success(service.submitReview(id,request.version(),request.idempotencyKey(),request.processDefinitionId()));}
    @PostMapping("/{id}/review-refresh") public CommonResult<BusinessOperationReceipt> refresh(@PathVariable Long id,@Valid @RequestBody Command request){return success(service.refreshReview(id,request.version(),request.idempotencyKey()));}
    @PostMapping("/{id}/copy-version") public CommonResult<BusinessOperationReceipt> copy(@PathVariable Long id,@Valid @RequestBody Command request){return success(service.copyVersion(id,request.version(),request.idempotencyKey()));}
    @PostMapping("/{id}/attach-customer-files") public CommonResult<BusinessOperationReceipt> files(@PathVariable Long id,@Valid @RequestBody Files request){return success(service.attachCustomerFiles(id,request.version(),request.idempotencyKey(),request.referenceIds()));}
}
