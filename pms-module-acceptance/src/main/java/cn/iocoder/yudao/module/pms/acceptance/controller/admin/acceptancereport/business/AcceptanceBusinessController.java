package cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptancereport.business;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.business.AcceptanceBusinessDO;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.business.AcceptanceBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/acceptance-business")
public class AcceptanceBusinessController extends ProjectBusinessController<AcceptanceBusinessService,AcceptanceBusinessDO> {
    @GetMapping("/{id}/reports/{reportId}/files") public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact>> files(@PathVariable Long id,@PathVariable Long reportId){return success(service.draftFiles(id,reportId));}
    public record Command(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey){ }
    public record Report(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Positive Long reportId,@NotNull @Positive Integer reportNo){ }
    public record Publish(@NotNull @PositiveOrZero Long version,@NotBlank String idempotencyKey,@NotNull @Positive Long reportId,@NotNull @Positive Integer reportNo,@Positive Long expectedCurrent){ }
    @PostMapping("/{id}/report-create") public CommonResult<BusinessOperationReceipt> create(@PathVariable Long id,@Valid @RequestBody Command request){return success(service.createReport(id,request.version(),request.idempotencyKey()));}
    @PostMapping("/{id}/report-save") public CommonResult<BusinessOperationReceipt> save(@PathVariable Long id,@Valid @RequestBody Report request){return success(service.saveReport(id,request.version(),request.idempotencyKey(),request.reportId(),request.reportNo()));}
    @PostMapping("/{id}/report-publish") public CommonResult<BusinessOperationReceipt> publish(@PathVariable Long id,@Valid @RequestBody Publish request){return success(service.publishReport(id,request.version(),request.idempotencyKey(),request.reportId(),request.reportNo(),request.expectedCurrent()));}
    @PostMapping("/{id}/report-revoke") public CommonResult<BusinessOperationReceipt> revoke(@PathVariable Long id,@Valid @RequestBody Report request){return success(service.revokeReport(id,request.version(),request.idempotencyKey(),request.reportId(),request.reportNo()));}
}
