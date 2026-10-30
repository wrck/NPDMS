package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Metadata-only authoring endpoint. The response is not an execution grant. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/project-templates/operation-catalog")
public class ProjectBusinessOperationCatalogController {
    private final ProjectBusinessOperationRegistry registry;

    public record Operation(String operationCode, int operationVersion, String label, String ownerAction,
            List<String> checkpoints, boolean runtimeAvailable, String permissionCode) {
        /** Existing Java metadata consumers remain source-compatible. */
        public Operation(String code, int version, String label, String action, List<String> checkpoints, boolean available) {
            this(code, version, label, action, checkpoints, available, null);
        }
    }
    public record Resolution(String status, Operation selected, List<Operation> candidates) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project-template:update') or @ss.hasPermission('pms:project-plan:manage')")
    public CommonResult<List<Operation>> list(@RequestParam String ownerContext, @RequestParam String objectType) {
        return CommonResult.success(registry.all().stream()
                .filter(item -> item.ownerContext().equals(ownerContext) && item.objectType().equals(objectType))
                .map(this::metadata).toList());
    }

    /** Resolves a configuration choice only. Caller permissions are not inferred from this result. */
    @GetMapping("/resolve")
    @PreAuthorize("@ss.hasPermission('pms:project-template:update') or @ss.hasPermission('pms:project-plan:manage')")
    public CommonResult<Resolution> resolve(@RequestParam String ownerContext, @RequestParam String objectType,
            @RequestParam String permissionCode, @RequestParam(required = false) String operationCode) {
        var result = registry.resolvePermission(ownerContext, objectType, permissionCode, operationCode);
        var candidates = result.candidates().stream().map(this::metadata).toList();
        return CommonResult.success(new Resolution(result.status().name(),
                result.selected() == null ? null : candidates.getFirst(), candidates));
    }

    private Operation metadata(ProjectBusinessOperationDescriptor item) {
        return new Operation(item.operationCode(), item.operationVersion(), item.label(), item.ownerAction(),
                item.checkpoints().stream().sorted().toList(), registry.runtimeAvailable(item.operationCode(), item.operationVersion()),
                registry.permissionCode(item.operationCode(), item.operationVersion()));
    }
}
