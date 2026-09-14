package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/plan")
@PreAuthorize("@ss.hasPermission('pms:project-plan:manage')")
public class ProjectPlanController {
    private final ProjectPlanDraftService service;
    private final cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanActivationService activation;
    public record Create(@NotNull Long expectedPlanVersionId) { }
    public record Save(@NotNull @PositiveOrZero Integer expectedVersion, @NotNull TemplateDesignerDocument designer) { }
    public record Apply(@NotNull ProjectPlanDraftService.Preview expectedPreview) { }

    @PostMapping("/draft/{draftId}/apply")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanActivationService.Applied> apply(
            @PathVariable("projectId") Long projectId, @PathVariable("draftId") Long draftId,
            @Valid @RequestBody Apply body, @RequestHeader("Idempotency-Key") String key) {
        return success(activation.apply(new cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanActivationService.Apply(
                projectId,draftId,body.expectedPreview()),SecurityFrameworkUtils.getLoginUserId(),key));
    }

    @GetMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectPlanDraftService.State> get(@PathVariable("projectId") Long projectId) {
        return success(service.get(projectId, SecurityFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/draft")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectPlanDraftService.Definition> create(@PathVariable("projectId") Long projectId,
            @Valid @RequestBody Create body, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(new ProjectPlanDraftService.Create(projectId, body.expectedPlanVersionId()), SecurityFrameworkUtils.getLoginUserId(), key));
    }
    @PutMapping("/draft/{draftId}")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectPlanDraftService.Definition> save(@PathVariable("projectId") Long projectId,
            @PathVariable("draftId") Long draftId, @Valid @RequestBody Save body, @RequestHeader("Idempotency-Key") String key) {
        try {
            return success(service.save(new ProjectPlanDraftService.Save(projectId, draftId, body.expectedVersion(), body.designer()), SecurityFrameworkUtils.getLoginUserId(), key));
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException expected) {
            throw expected;
        } catch (RuntimeException failed) {
            // Rule literals must not be copied into the global unexpected-error request log.
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_CHANGE_INVALID);
        }
    }
    @GetMapping("/draft/{draftId}/preview")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectPlanDraftService.Preview> preview(@PathVariable("projectId") Long projectId,
            @PathVariable("draftId") Long draftId, @RequestParam("expectedVersion") Integer expectedVersion) {
        return success(service.preview(projectId, draftId, expectedVersion, SecurityFrameworkUtils.getLoginUserId()));
    }
}
