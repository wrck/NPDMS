package cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.DefaultBusinessDeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Shared default business endpoint. Entity permissions and project ownership are checked by the default service. */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/business-models")
public class DefaultBusinessDeliveryController {
    private final DefaultBusinessDeliveryService service;
    public record EditRequest(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=255) String title) { }
    @GetMapping("/{ownerModule}/{entityType}/deliverables/context")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<Scope> context(@PathVariable String ownerModule,@PathVariable String entityType,@RequestParam String entityId) {
        return success(service.context(ownerModule,entityType,entityId));
    }
    @PostMapping(value="/deliverables/upload",consumes="multipart/form-data")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<Record> upload(@RequestParam @Positive Long projectId,@RequestParam String businessType,
            @RequestParam String businessEntityKey,@RequestParam String deliverableType,@RequestPart MultipartFile file,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.upload(new Scope(projectId,businessType,businessEntityKey,deliverableType),file,key));
    }
    @GetMapping("/deliverables") @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<PageResult<Record>> list(@RequestParam @Positive Long projectId,@RequestParam(required=false) String deliverableType,
            @RequestParam(required=false) String businessType,@RequestParam(required=false) String businessEntityKey,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        return success(service.list(projectId,deliverableType,businessType,businessEntityKey,pageNo,pageSize));
    }
    @GetMapping("/deliverables/completion") @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<Completion> completion(@RequestParam @Positive Long projectId,@RequestParam String businessType,
            @RequestParam String businessEntityKey,@RequestParam String deliverableType) {
        return success(service.completion(new Scope(projectId,businessType,businessEntityKey,deliverableType)));
    }
    @GetMapping("/deliverables/{id}/file") @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Document> file(@PathVariable @Positive Long id){return success(service.file(id));}
    @GetMapping("/deliverables/{id}") @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<Record> get(@PathVariable @Positive Long id){return success(service.get(id));}
    @PutMapping("/deliverables/{id}") @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<Record> edit(@PathVariable @Positive Long id,@Valid @RequestBody EditRequest request){return success(service.edit(id,request.version(),request.title()));}
    @DeleteMapping("/deliverables/{id}") @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<Boolean> delete(@PathVariable @Positive Long id,@RequestParam @PositiveOrZero Long version){service.delete(id,version);return success(true);}
}
