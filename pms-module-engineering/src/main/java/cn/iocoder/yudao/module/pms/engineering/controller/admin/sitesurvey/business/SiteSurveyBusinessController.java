package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.business.SiteSurveyBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pms/site-survey-business")
public class SiteSurveyBusinessController extends ProjectBusinessController<SiteSurveyBusinessService,SiteSurveyEntityDO> {
    public record Action(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey){}
    @PostMapping("/{id}/confirm") public CommonResult<BusinessOperationReceipt> confirm(@PathVariable Long id,@Valid @RequestBody Action action){return CommonResult.success(service.confirm(id,action.version(),action.idempotencyKey()));}
    @PostMapping("/{id}/reject") public CommonResult<BusinessOperationReceipt> reject(@PathVariable Long id,@Valid @RequestBody Action action){return CommonResult.success(service.reject(id,action.version(),action.idempotencyKey()));}
    @PostMapping("/{id}/archive") public CommonResult<BusinessOperationReceipt> archive(@PathVariable Long id,@Valid @RequestBody Action action){return CommonResult.success(service.archive(id,action.version(),action.idempotencyKey()));}
    public record LocationAction(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotBlank String description,
            @NotNull @Valid cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand location){}
    public record DeadlineAction(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @PositiveOrZero Long projectVersion,@NotNull java.time.LocalDate endDate){}
    @PostMapping("/{id}/location") public CommonResult<BusinessOperationReceipt> location(@PathVariable Long id,@Valid @RequestBody LocationAction action){
        return CommonResult.success(service.maintainLocation(id,action.version(),action.idempotencyKey(),action.description(),action.location()));
    }
    @PostMapping("/{id}/deadline") public CommonResult<BusinessOperationReceipt> deadline(@PathVariable Long id,@Valid @RequestBody DeadlineAction action){
        return CommonResult.success(service.updateDeadline(id,action.version(),action.idempotencyKey(),action.projectVersion(),action.endDate()));
    }
}
