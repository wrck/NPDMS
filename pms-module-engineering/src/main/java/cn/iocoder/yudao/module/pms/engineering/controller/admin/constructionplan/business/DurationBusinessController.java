package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.business;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.business.DurationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.business.DurationBusinessService;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.*;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/duration-business")
public class DurationBusinessController extends ProjectBusinessController<DurationBusinessService,DurationBusinessDO>{
    public record Create(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Valid DurationChangeCreateReqVO content){ }
    public record Save(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Positive Long changeId,@NotNull @PositiveOrZero Integer changeVersion,@NotNull @Valid DurationChangePatchReqVO content){ }
    public record Submit(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Positive Long changeId,@NotNull @PositiveOrZero Integer changeVersion,@NotNull @PositiveOrZero Long projectVersion){ }
    @PostMapping("/{id}/change-create") public CommonResult<BusinessOperationReceipt> createChange(@PathVariable Long id,@Valid @RequestBody Create request){return success(service.createChange(id,request.version(),request.idempotencyKey(),request.content()));}
    @PostMapping("/{id}/change-save") public CommonResult<BusinessOperationReceipt> saveChange(@PathVariable Long id,@Valid @RequestBody Save request){return success(service.saveChange(id,request.version(),request.idempotencyKey(),request.changeId(),request.changeVersion(),request.content()));}
    @PostMapping("/{id}/change-submit") public CommonResult<BusinessOperationReceipt> submitChange(@PathVariable Long id,@Valid @RequestBody Submit request){return success(service.submitChange(id,request.version(),request.idempotencyKey(),request.changeId(),request.changeVersion(),request.projectVersion()));}
    @GetMapping("/{id}/duration/revisions") public CommonResult<ConstructionPlanCursorPageRespVO<ConstructionPlanRevisionRespVO>> revisions(@PathVariable Long id,@Valid ConstructionPlanRevisionPageReqVO page){return success(service.revisions(id,page));}
    @GetMapping("/{id}/duration/changes") public CommonResult<ConstructionPlanCursorPageRespVO<ConstructionPlanChangeRespVO>> changes(@PathVariable Long id,@Valid ConstructionPlanChangePageReqVO page){return success(service.changes(id,page));}
}
