package cn.iocoder.yudao.module.pms.platform.support.controller;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.support.service.DefaultBusinessApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** A thin business controller supplies only its route, default service and fixed business identity. */
public abstract class DefaultBusinessController extends BusinessModelViews {
    private final DefaultBusinessApplicationService service;
    private final String owner;
    private final String type;
    protected DefaultBusinessController(DefaultBusinessApplicationService service, String owner, String type) {
        this.service = Objects.requireNonNull(service); this.owner = owner; this.type = type;
    }
    @GetMapping public CommonResult<ModelDetailVO> model() { return success(service.modelView(owner, type)); }
    @GetMapping("/data") public CommonResult<BusinessEntityData> data(@RequestParam @Positive Long id) { return success(service.read(owner, type, id)); }
    @PostMapping("/page") public CommonResult<BusinessEntitySlice> page(@Valid @RequestBody PageQueryReqVO request) {
        var filters = request.getFilters() == null ? List.<BusinessFieldFilter>of() : request.getFilters().stream()
                .map(filter -> new BusinessFieldFilter(filter.getFieldCode(), filter.getOperator(), filter.getValues())).toList();
        return success(service.query(new BusinessEntityPageQuery(request.getSceneCode(), owner, type, filters, request.getPageSize(), request.getCursor())));
    }
    @PostMapping("/operations/{operationCode}") public CommonResult<BusinessOperationReceipt> execute(
            @PathVariable String operationCode, @RequestParam(required=false) @Positive Long entityId,
            @Valid @RequestBody OperationExecuteReqVO request) { return success(service.executeBound(owner, type, operationCode, entityId, request)); }
    @GetMapping("/operations/{operationCode}/receipt") public CommonResult<BusinessOperationReceipt> receipt(
            @PathVariable String operationCode, @RequestParam int operationVersion, @RequestParam String idempotencyKey) {
        return success(service.recoverReceipt(owner, type, operationCode, operationVersion, idempotencyKey));
    }
    @GetMapping("/deliverables/context") public CommonResult<Scope> context(@RequestParam String entityId) { return success(service.deliveryContext(owner, type, entityId)); }
    private Scope scope(String entityId, String deliverableType) {
        var context = service.deliveryContext(owner, type, entityId);
        return new Scope(context.projectId(), context.businessType(), context.businessEntityKey(), deliverableType);
    }
    @PostMapping(value="/deliverables/upload", consumes="multipart/form-data") public CommonResult<Record> upload(
            @RequestParam String entityId, @RequestParam String deliverableType, @RequestPart MultipartFile file,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.uploadDelivery(scope(entityId, deliverableType), new UploadFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), () -> {
            try { return file.getInputStream(); } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }), key));
    }
    @GetMapping("/deliverables") public CommonResult<PageResult<Record>> list(@RequestParam String entityId,
            @RequestParam(required=false) String deliverableType, @RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize) {
        var context = service.deliveryContext(owner, type, entityId);
        return success(service.listDeliveries(context.projectId(), deliverableType, context.businessType(), context.businessEntityKey(), pageNo, pageSize));
    }
    @GetMapping("/deliverables/completion") public CommonResult<Completion> completion(@RequestParam String entityId,
            @RequestParam String deliverableType) { return success(service.deliveryCompletion(scope(entityId, deliverableType))); }
    private Record owned(Long id) {
        var record = service.getDelivery(id);
        if (!owner.equals(record.ownerModule()) || !type.equals(record.entityType()))
            throw new BusinessContractException("DELIVERY_OWNER_MISMATCH", "Material belongs to another business route");
        return record;
    }
    public record EditRequest(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=255) String title) { }
    @GetMapping("/deliverables/{id}") public CommonResult<Record> get(@PathVariable @Positive Long id) { return success(owned(id)); }
    @GetMapping("/deliverables/{id}/file") public CommonResult<FileEvidenceApi.Document> file(@PathVariable @Positive Long id) { owned(id); return success(service.deliveryFile(id)); }
    @PutMapping("/deliverables/{id}") public CommonResult<Record> edit(@PathVariable @Positive Long id, @Valid @RequestBody EditRequest request) { owned(id); return success(service.editDelivery(id, request.version(), request.title())); }
    @DeleteMapping("/deliverables/{id}") public CommonResult<Boolean> delete(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version) { owned(id); service.deleteDelivery(id, version); return success(true); }
}
