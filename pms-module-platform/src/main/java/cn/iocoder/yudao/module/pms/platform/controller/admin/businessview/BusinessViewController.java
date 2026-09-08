package cn.iocoder.yudao.module.pms.platform.controller.admin.businessview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewApplicationService;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** PM-03 / F-PLT-003 / SDS10: body cannot override Owner, schema, actions, tenant or actor. */
@RestController
@RequestMapping("/api/v1/pms/business-views")
@Tag(name = "管理后台 - PMS 业务视图注册")
@Validated
@RequiredArgsConstructor
public class BusinessViewController {
    private final BusinessViewApplicationService service;

    public record SelectionRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,63}") String entityType,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String viewKey,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String componentKey,
            @NotBlank @Size(max = 64) String componentVersion,
            @Positive Long dynamicFormRevisionId) {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unsupported business-view selection field: " + name);
        }
        public BusinessViewApplicationService.Selection selection() {
            return new BusinessViewApplicationService.Selection(entityType, viewKey, componentKey,
                    componentVersion, dynamicFormRevisionId);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PageRequest extends PageParam {
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,63}")
        private String entityType;
        private BusinessViewComponentProvider.ViewSource viewSource;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:business-view:query')")
    public CommonResult<PageResult<BusinessViewRevision>> page(@Valid PageRequest request) {
        return success(service.page(request.getPageNo(), request.getPageSize(), request.getEntityType(), request.getViewSource()));
    }

    @GetMapping("/components")
    @PreAuthorize("@ss.hasPermission('pms:business-view:query')")
    public CommonResult<List<BusinessViewComponentProvider.Component>> components() {
        return success(service.components());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:business-view:query')")
    public CommonResult<BusinessViewRevision> get(@PathVariable @Positive Long id) {
        return success(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:business-view:manage')")
    public CommonResult<BusinessViewRevision> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody SelectionRequest request) {
        return success(service.create(key, request.selection()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:business-view:manage')")
    public CommonResult<BusinessViewRevision> update(@PathVariable @Positive Long id,
            @RequestHeader("If-Match") @Min(0) Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody SelectionRequest request) {
        return success(service.update(id, version, key, request.selection()));
    }

    @PostMapping("/{id}/actions/copy")
    @PreAuthorize("@ss.hasPermission('pms:business-view:manage')")
    public CommonResult<BusinessViewRevision> copy(@PathVariable @Positive Long id,
            @RequestHeader("If-Match") @Min(0) Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return success(service.copy(id, version, key));
    }

    @PostMapping("/{id}/actions/validate")
    @PreAuthorize("@ss.hasPermission('pms:business-view:manage')")
    public CommonResult<BusinessViewApplicationService.Validation> validate(@PathVariable @Positive Long id) {
        return success(service.validate(id));
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("@ss.hasPermission('pms:business-view:publish')")
    public CommonResult<BusinessViewRevision> publish(@PathVariable @Positive Long id,
            @RequestHeader("If-Match") @Min(0) Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return success(service.publish(id, version, key));
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("@ss.hasPermission('pms:business-view:disable')")
    public CommonResult<BusinessViewRevision> disable(@PathVariable @Positive Long id,
            @RequestHeader("If-Match") @Min(0) Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return success(service.disable(id, version, key));
    }
}
