package cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ExecutionBackendCapability;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.RevisionRef;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 统一业务模型公共入口：目录、详情、受控查询与操作分发全部由声明驱动，
 * 不按实体名称写死任何分支；前端禁用状态不代替后端授权。
 */
@RestController
@RequestMapping("/api/v1/pms/business-models")
@Tag(name = "管理后台 - PMS 统一业务模型")
@Validated
@RequiredArgsConstructor
public class BusinessModelController extends cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews {

    private final BusinessModelCatalog catalog;
    private final BusinessAccessGuard guard;
    private final BusinessEntityAccessPort accessPort;
    private final BusinessOperationDispatcher dispatcher;
    private final ObjectProvider<ExecutionBackendCapability> executionBackends;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<List<ModelSummaryVO>> catalog() {
        EntityActor actor = actor();
        return success(catalog.all().stream()
                .filter(descriptor -> readable(descriptor, actor, "catalog"))
                .map(ModelSummaryVO::of).toList());
    }

    @GetMapping("/{ownerModule}/{entityType}")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<ModelDetailVO> detail(@PathVariable String ownerModule,
                                              @PathVariable String entityType) {
        BusinessModelDescriptor descriptor = catalog.require(ownerModule, entityType);
        EntityActor actor = actor();
        guard.requireReadable(descriptor, actor, "detail");
        return success(ModelDetailVO.of(descriptor, actor, guard));
    }

    /** 当前装配声明的执行后端能力：配置界面据此显示授权能力与不支持语义，空列表即未装配独立执行后端。 */
    @GetMapping("/execution-capabilities")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<List<ExecutionBackendCapability>> executionCapabilities() {
        return success(executionBackends.stream().toList());
    }

    @PostMapping("/{ownerModule}/{entityType}/page")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<BusinessEntitySlice> page(@PathVariable String ownerModule,
                                                  @PathVariable String entityType,
                                                  @Valid @RequestBody PageQueryReqVO request) {
        List<BusinessFieldFilter> filters = request.getFilters() == null ? List.of()
                : request.getFilters().stream()
                        .map(filter -> new BusinessFieldFilter(filter.getFieldCode(),
                                filter.getOperator(), filter.getValues()))
                        .toList();
        var query = new BusinessEntityPageQuery(
                request.getSceneCode(), ownerModule, entityType, filters,
                request.getPageSize(), request.getCursor());
        // Explicit default adoption uses inherited query overrides. Existing revision/native readers
        // retain their original projection and access contract until their own migration is approved.
        return success(catalog.require(ownerModule, entityType).scopeBinding() == null
                ? accessPort.query(query, actor()) : dispatcher.query(query));
    }

    @GetMapping("/{ownerModule}/{entityType}/data")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<BusinessEntityData> data(@PathVariable String ownerModule,
                                                 @PathVariable String entityType,
                                                 @RequestParam @Positive Long id,
                                                 @RequestParam(required = false) @Positive Long revisionId) {
        EntityRef ref = new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule,
                entityType, id);
        EntityDataRef dataRef = revisionId == null ? EntityDataRef.current(ref)
                : EntityDataRef.revision(new RevisionRef(ref, revisionId));
        return success(accessPort.read(dataRef, actor(), "detail"));
    }

    @PostMapping("/{ownerModule}/{entityType}/operations/{operationCode}")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<BusinessOperationReceipt> execute(@PathVariable String ownerModule,
                                                          @PathVariable String entityType,
                                                          @PathVariable String operationCode,
                                                          @RequestParam(required = false) @Positive Long entityId,
                                                          @Valid @RequestBody OperationExecuteReqVO request) {
        BusinessModelDescriptor descriptor = catalog.require(ownerModule, entityType);
        BusinessOperationDescriptor operation = descriptor.operations().stream()
                .filter(op -> op.code().equals(operationCode)).findFirst()
                .orElseThrow(() -> new BusinessContractException("OPERATION_NOT_DECLARED",
                        "操作未在目录声明: " + operationCode));
        if (request.getRevisionId() != null && entityId == null) {
            throw new BusinessContractException("OPERATION_INPUT_INVALID", "修订目标必须携带逻辑实体身份");
        }
        EntityDataRef targetRef = entityId == null ? null : new EntityDataRef(
                new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule, entityType, entityId), request.getRevisionId());
        BusinessOperationRequest operationRequest = new BusinessOperationRequest(
                operation.code(), operation.version(), targetRef, ownerModule, entityType,
                request.getInput() == null ? Map.of() : request.getInput(),
                request.getIdempotencyKey(), request.getConcurrencyBasis(),
                request.getEntryKind() == null ? OperationEntryKind.INDEPENDENT : request.getEntryKind(),
                request.getEntryCorrelationId());
        return success(dispatcher.dispatch(operationRequest));
    }

    private EntityActor actor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessContractException("CALLER_UNRESOLVED", "缺少可信操作者身份");
        }
        return new EntityActor(TenantContextHolder.getRequiredTenantId(), userId, null);
    }

    private boolean readable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        try {
            guard.requireReadable(descriptor, actor, sceneCode);
            return true;
        } catch (BusinessContractException ex) {
            return false;
        }
    }

}
