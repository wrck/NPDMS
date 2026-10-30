package cn.iocoder.yudao.module.pms.project.controller.admin.projectexecution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationCapabilities;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationCapabilityQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/pms/project-execution/capabilities")
public class ProjectOperationCapabilityController {
    private final ProjectOperationCapabilityQueryService queries;
    public record Inspection(@NotNull @Positive Long projectId, @NotNull @Pattern(regexp = "TASK|STAGE") String nodeKind,
            @NotNull @Positive Long nodeId, @Size(max = 128) String objectId, ProjectBusinessExecutionSelection expected) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project-task:query') or @ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectOperationCapabilities> get(@RequestParam @Positive Long projectId,
            @RequestParam @Pattern(regexp = "TASK|STAGE") String nodeKind, @RequestParam @Positive Long nodeId,
            @RequestParam(required = false) @Size(max = 128) String objectId) {
        return CommonResult.success(queries.inspect(projectId, nodeKind, nodeId, objectId, null));
    }

    /** Read-only inspection with an expected version vector; does not grant a command token. */
    @PostMapping("/inspect")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query') or @ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectOperationCapabilities> inspect(@Valid @RequestBody Inspection request) {
        return CommonResult.success(queries.inspect(request.projectId(), request.nodeKind(), request.nodeId(), request.objectId(), request.expected()));
    }
}
