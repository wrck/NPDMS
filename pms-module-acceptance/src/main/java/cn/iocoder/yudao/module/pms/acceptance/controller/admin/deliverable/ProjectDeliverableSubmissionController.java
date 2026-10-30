package cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverable;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableSubmissionService;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableSubmissionService.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/deliverables/{id}")
public class ProjectDeliverableSubmissionController {
    private final ProjectDeliverableSubmissionService service;
    @GetMapping @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<Detail> detail(@PathVariable Long projectId, @PathVariable Long id) { return success(service.detail(projectId, id)); }
    @GetMapping("/result-types") @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<BusinessResultSource.Descriptor>> types(@PathVariable Long projectId, @PathVariable Long id) {
        return success(service.resultTypes(projectId, id));
    }
    @GetMapping("/result-candidates") @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<BusinessResultInventorySource.InventoryPage> candidates(@PathVariable Long projectId, @PathVariable Long id,
            @RequestParam String ownerContext, @RequestParam String entityType, @RequestParam String resultType,
            @RequestParam(required = false) String after) {
        return success(service.candidates(projectId, id, new BusinessResultSource.Type(ownerContext, entityType, resultType), after));
    }
    @PostMapping("/submissions") @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Submitted> submit(@PathVariable Long projectId, @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String requestKey, @Valid @RequestBody Submission request) {
        return success(service.submit(projectId, id, requestKey, request));
    }
    @PostMapping("/evaluate") @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Evaluation> evaluate(@PathVariable Long projectId, @PathVariable Long id) { return success(service.refresh(projectId, id)); }
}
