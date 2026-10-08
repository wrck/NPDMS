package cn.iocoder.yudao.module.pms.platform.controller.admin.business;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Common material endpoint. The shared service enforces each owner's permissions and current project scope. */
@RestController @Validated @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/pms/business-deliverables")
public class ProjectBusinessDeliveryController {
    private final DefaultBusinessDeliveryApi service;
    public record EditRequest(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=255) String title) { }
    @GetMapping public CommonResult<PageResult<Record>> list(@RequestParam @Positive Long projectId,
            @RequestParam(required=false) String deliverableType,@RequestParam(required=false) String businessType,
            @RequestParam(required=false) String businessEntityKey,@RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize) {
        return success(service.list(projectId,deliverableType,businessType,businessEntityKey,pageNo,pageSize));
    }
    @PostMapping(consumes="multipart/form-data") public CommonResult<Record> upload(@RequestParam @Positive Long projectId,
            @RequestParam String businessType,@RequestParam String businessEntityKey,@RequestParam String deliverableType,
            @RequestPart MultipartFile file,@RequestHeader("Idempotency-Key") String key) {
        return success(service.upload(new Scope(projectId,businessType,businessEntityKey,deliverableType),
                new UploadFile(file.getOriginalFilename(),file.getContentType(),file.getSize(),()->{
                    try{return file.getInputStream();}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                }),key));
    }
    @GetMapping("/completion") public CommonResult<Completion> completion(@RequestParam @Positive Long projectId,
            @RequestParam String businessType,@RequestParam String businessEntityKey,@RequestParam String deliverableType) {
        return success(service.completion(new Scope(projectId,businessType,businessEntityKey,deliverableType)));
    }
    @GetMapping("/{id}") public CommonResult<Record> get(@PathVariable @Positive Long id){return success(service.get(id));}
    @GetMapping("/{id}/file") public CommonResult<FileEvidenceApi.Document> file(@PathVariable @Positive Long id){return success(service.file(id));}
    @PutMapping("/{id}") public CommonResult<Record> edit(@PathVariable @Positive Long id,@Valid @RequestBody EditRequest request){return success(service.edit(id,request.version(),request.title()));}
    @DeleteMapping("/{id}") public CommonResult<Boolean> delete(@PathVariable @Positive Long id,@RequestParam @PositiveOrZero Long version){service.delete(id,version);return success(true);}
}
