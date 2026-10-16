package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.business;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.business.StagePlanBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.stageplan.business.StagePlanBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/stage-plan-business")
public class StagePlanBusinessController extends ProjectBusinessController<StagePlanBusinessService,StagePlanBusinessDO>{
    public record Command(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey){ }
    public record Submit(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Positive Long approverUserId,String adjustmentReason){ }
    @PostMapping("/{id}/estimate") public CommonResult<BusinessOperationReceipt> estimate(@PathVariable Long id,@Valid @RequestBody Command request){return success(service.estimate(id,request.version(),request.idempotencyKey()));}
    @PostMapping("/{id}/submit") public CommonResult<BusinessOperationReceipt> submit(@PathVariable Long id,@Valid @RequestBody Submit request){return success(service.submit(id,request.version(),request.idempotencyKey(),request.approverUserId(),request.adjustmentReason()));}
}
