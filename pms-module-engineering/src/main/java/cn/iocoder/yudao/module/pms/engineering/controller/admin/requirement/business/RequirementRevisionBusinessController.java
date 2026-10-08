package cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.business.RequirementRevisionBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/pms/requirement-analysis-business")
public class RequirementRevisionBusinessController extends ProjectBusinessController<RequirementRevisionBusinessService,RequirementAnalysisRevisionDO> {
    public record Action(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,String reason){}
    @PostMapping("/{id}/complete") public CommonResult<BusinessOperationReceipt> complete(@PathVariable Long id,@Valid @RequestBody Action action){return CommonResult.success(service.complete(id,action.version(),action.idempotencyKey()));}
    @PostMapping("/{id}/copy") public CommonResult<BusinessOperationReceipt> copy(@PathVariable Long id,@Valid @RequestBody Action action){return CommonResult.success(service.copyRevision(id,action.version(),action.idempotencyKey(),action.reason()));}
}
