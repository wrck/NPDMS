package cn.iocoder.yudao.module.pms.platform.support.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Version-enabled business Controllers inherit this as well as the ordinary CRUD/form/delivery APIs. */
public abstract class VersionedProjectBusinessController<S extends ProjectBusinessService<E> & BusinessRevisions,E extends BaseProjectBusinessEntity>
        extends ProjectBusinessController<S,E> {
    public record CreateRevision(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=128) String idempotencyKey,
            @Positive Long sourceRevisionId,@Size(max=255) String reason){}
    public record RevisionAction(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=128) String idempotencyKey,
            @NotNull @Positive Long revisionId,@NotNull @PositiveOrZero Long revisionVersion,Map<String,Object> values){}
    @GetMapping("/{id}/revisions") public CommonResult<List<EntityVersionProvider.Revision>> revisions(@PathVariable @Positive Long id,@RequestParam(required=false) Long beforeId,@RequestParam(defaultValue="50") int limit){return success(service.revisions(id,beforeId,limit));}
    @GetMapping("/{id}/revisions/{revisionId}") public CommonResult<Map<String,EntityFieldValue>> revision(@PathVariable @Positive Long id,@PathVariable @Positive Long revisionId){return success(service.revisionValues(id,revisionId));}
    @GetMapping("/{id}/revisions/{revisionId}/form") public CommonResult<BusinessFormData> form(@PathVariable @Positive Long id,@PathVariable @Positive Long revisionId){return success(service.revisionForm(id,revisionId));}
    @GetMapping("/{id}/revisions/compare") public CommonResult<List<EntityVersionApi.FieldDifference>> compare(@PathVariable @Positive Long id,@RequestParam @Positive Long left,@RequestParam @Positive Long right){return success(service.compareRevisions(id,left,right));}
    @PostMapping("/{id}/revision-create") public CommonResult<BusinessOperationReceipt> createRevision(@PathVariable @Positive Long id,@Valid @RequestBody CreateRevision request){return success(service.createRevision(id,request.version(),request.sourceRevisionId(),request.reason(),request.idempotencyKey()));}
    @PostMapping("/{id}/revision-save") public CommonResult<BusinessOperationReceipt> saveRevision(@PathVariable @Positive Long id,@Valid @RequestBody RevisionAction request){return success(service.saveRevision(id,request.version(),request.revisionId(),request.revisionVersion(),request.values(),request.idempotencyKey()));}
    @PostMapping("/{id}/revision-complete") public CommonResult<BusinessOperationReceipt> completeRevision(@PathVariable @Positive Long id,@Valid @RequestBody RevisionAction request){return success(service.completeRevision(id,request.version(),request.revisionId(),request.revisionVersion(),request.idempotencyKey()));}
    @PostMapping("/{id}/revision-discard") public CommonResult<BusinessOperationReceipt> discardRevision(@PathVariable @Positive Long id,@Valid @RequestBody RevisionAction request){return success(service.discardRevision(id,request.version(),request.revisionId(),request.revisionVersion(),request.idempotencyKey()));}
}
