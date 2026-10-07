package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** A business supplies its route and generic service type; all normal API methods are inherited. */
@Validated
public abstract class ProjectBusinessController<S extends ProjectBusinessService<E>, E extends BaseProjectBusinessEntity> {
    @Autowired protected S service;
    public record WriteBody(@NotBlank @Size(max=128) String idempotencyKey, @PositiveOrZero Long version,
                            @NotNull Map<String,Object> values) { }
    public record MaterialEdit(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=255) String title) { }

    public record FieldConfigurationWrite(@Min(0) long version,@NotNull java.util.List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field> fields) { }
    @GetMapping("/field-configuration/defaults") public CommonResult<BusinessModelViews.ModelDetailVO> fieldDefaults(){return success(service.configurationModel(false));}
    @GetMapping("/field-configuration") public CommonResult<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration> fieldConfiguration(){return success(service.fieldConfiguration());}
    @PutMapping("/field-configuration") public CommonResult<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration> saveFieldConfiguration(@Valid @RequestBody FieldConfigurationWrite request){return success(service.saveFieldConfiguration(request.version(),request.fields()));}
    @GetMapping("/model") public CommonResult<BusinessModelViews.ModelDetailVO> model() { return success(service.model()); }
    @GetMapping("/{id}") public CommonResult<BusinessEntityData> get(@PathVariable @Positive Long id) { return success(view(service.get(id))); }
    @PostMapping("/page") public CommonResult<PageResult<BusinessEntityData>> page(@Valid @RequestBody BusinessPageQuery query) {
        var result=service.page(query);return success(new PageResult<>(result.getList().stream().map(this::view).toList(),result.getTotal()));
    }
    @PostMapping public CommonResult<BusinessOperationReceipt> create(@Valid @RequestBody WriteBody request) {
        return success(service.create(service.input(request.values()),request.idempotencyKey()));
    }
    @PutMapping("/{id}") public CommonResult<BusinessOperationReceipt> update(@PathVariable @Positive Long id,@Valid @RequestBody WriteBody request) {
        return success(service.update(id,service.input(request.values()),request.values().keySet(),request.version(),request.idempotencyKey()));
    }
    @DeleteMapping("/{id}") public CommonResult<BusinessOperationReceipt> delete(@PathVariable @Positive Long id,
            @RequestParam @PositiveOrZero Long version,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String key) {
        return success(service.delete(id,version,key));
    }
    @GetMapping("/receipts/{key}") public CommonResult<BusinessOperationReceipt> receipt(@PathVariable String key,@RequestParam String operation) {
        return success(service.receipt(operation,key));
    }
    @GetMapping("/{id}/form") public CommonResult<BusinessFormData> form(@PathVariable @Positive Long id){return success(service.form(id));}
    @PostMapping("/{id}/save-form") public CommonResult<BusinessOperationReceipt> saveForm(@PathVariable @Positive Long id,@Valid @RequestBody WriteBody request){
        return success(service.saveForm(id,request.values(),request.version(),request.idempotencyKey()));
    }
    @GetMapping("/{id}/deliverables/context") public CommonResult<DefaultBusinessDeliveryApi.Scope> deliveryContext(@PathVariable @Positive Long id) {
        return success(service.deliveryScope(id,null));
    }
    @PostMapping(value="/{id}/deliverables",consumes="multipart/form-data") public CommonResult<Record> upload(
            @PathVariable @Positive Long id,@RequestParam String deliverableType,@RequestParam @Positive Long projectId,
            @RequestParam String businessType,@RequestParam String businessEntityKey,@RequestPart MultipartFile file,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.uploadDelivery(id,new DefaultBusinessDeliveryApi.Scope(projectId,businessType,businessEntityKey,deliverableType),new DefaultBusinessDeliveryApi.UploadFile(file.getOriginalFilename(),file.getContentType(),file.getSize(),()->{
            try{return file.getInputStream();}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        }),key));
    }
    @GetMapping("/{id}/deliverables") public CommonResult<PageResult<Record>> deliveries(@PathVariable @Positive Long id,
            @RequestParam(required=false) String deliverableType,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        return success(service.deliveries(id,deliverableType,pageNo,pageSize));
    }
    @GetMapping("/{id}/deliverables/history") public CommonResult<PageResult<Record>> deliveryHistory(@PathVariable @Positive Long id,
            @RequestParam(required=false) String deliverableType,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        return success(service.deliveryHistory(id,deliverableType,pageNo,pageSize));
    }
    @GetMapping("/{id}/deliverables/completion") public CommonResult<DefaultBusinessDeliveryApi.Completion> completion(@PathVariable @Positive Long id,@RequestParam String deliverableType,
            @RequestParam @Positive Long projectId,@RequestParam String businessType,@RequestParam String businessEntityKey) {
        return success(service.deliveryCompletion(id,new DefaultBusinessDeliveryApi.Scope(projectId,businessType,businessEntityKey,deliverableType)));
    }
    @GetMapping("/{id}/deliverables/{materialId}") public CommonResult<Record> delivery(@PathVariable @Positive Long id,@PathVariable @Positive Long materialId) {
        return success(service.delivery(id,materialId));
    }
    @GetMapping("/{id}/deliverables/{materialId}/file") public CommonResult<FileEvidenceApi.Document> file(@PathVariable @Positive Long id,@PathVariable @Positive Long materialId) {
        return success(service.deliveryFile(id,materialId));
    }
    @PutMapping("/{id}/deliverables/{materialId}") public CommonResult<Record> editDelivery(@PathVariable @Positive Long id,
            @PathVariable @Positive Long materialId,@Valid @RequestBody MaterialEdit request) {
        return success(service.editDelivery(id,materialId,request.version(),request.title()));
    }
    @DeleteMapping("/{id}/deliverables/{materialId}") public CommonResult<Boolean> deleteDelivery(@PathVariable @Positive Long id,
            @PathVariable @Positive Long materialId,@RequestParam @PositiveOrZero Long version) {
        service.deleteDelivery(id,materialId,version);return success(true);
    }
    protected BusinessEntityData view(E entity) {
        var model=service.definition();return new BusinessEntityData(new EntityRef(entity.getTenantId(),model.ownerModule(),model.entityType(),entity.getId()),
                null,service.readableValues(entity),entity.getVersion(),true,null);
    }
}
