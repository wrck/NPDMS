package cn.iocoder.yudao.module.pms.engineering.controller.admin.solutionreview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewDO;
import cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/solution-reviews")
public class SolutionReviewController {
    private final SolutionTieredReviewService service;
    private final SolutionReviewBpmApi bpm;
    private final cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewReadMaterials materials;
    @GetMapping("/submission-readiness") @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectTaskPrerequisiteApi.Readiness> submissionReadiness(@RequestParam Long projectId) {
        return success(service.submissionReadiness(projectId));
    }
    @GetMapping("/policy") @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewPolicyService.Policy> policy(@RequestParam Long projectId) {
        return success(service.policy(projectId));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<Object> invalid(IllegalArgumentException error) { return CommonResult.error(400,error.getMessage()); }
    @GetMapping("/definition") @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<SolutionReviewBpmApi.Definition> definition() { return success(bpm.definition(TenantContextHolder.getRequiredTenantId())); }
    @GetMapping("/source") @PreAuthorize("@ss.hasAnyPermissions('pms:sol-solution:query','pms:sol-solution:major-review')")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionRespVO> source(@RequestParam String businessKey) {
        return success(cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(service.sourceForReview(businessKey),
                cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionRespVO.class));
    }
    @GetMapping("/materials") @PreAuthorize("@ss.hasAnyPermissions('pms:sol-solution:query','pms:sol-solution:major-review')")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewReadMaterials.Materials> materials(@RequestParam String businessKey) {
        return success(materials.read(businessKey));
    }
    @GetMapping("/attachment") @PreAuthorize("@ss.hasAnyPermissions('pms:sol-solution:query','pms:sol-solution:major-review')")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public org.springframework.http.ResponseEntity<byte[]> attachment(@RequestParam String businessKey, @RequestParam Long referenceId) {
        var content = materials.content(new cn.iocoder.yudao.module.pms.platform.api.file.OwnerScopedFileContentApi.Query("SOL_REVIEW", businessKey, referenceId));
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(content.name(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.parseMediaType(content.mediaType() == null ? "application/octet-stream" : content.mediaType()))
                .body(content.bytes());
    }
    @GetMapping @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<SolutionReviewDO> read(@RequestParam Long projectId, @RequestParam Long solutionId) {
        return success(service.read(new SolutionTieredReviewService.Selection(projectId, solutionId)));
    }
    @PostMapping @PreAuthorize("@ss.hasPermission('pms:sol-solution:update')")
    public CommonResult<SolutionReviewDO> start(@jakarta.validation.Valid @RequestBody SolutionTieredReviewService.Start command) { return success(service.start(command)); }
    @PostMapping("/refresh") @PreAuthorize("@ss.hasPermission('pms:sol-solution:update')")
    public CommonResult<SolutionReviewDO> refresh(@jakarta.validation.Valid @RequestBody SolutionTieredReviewService.Selection selection) { return success(service.refresh(selection)); }
    @PostMapping("/revise") @PreAuthorize("@ss.hasPermission('pms:sol-solution:create')")
    public CommonResult<Long> revise(@jakarta.validation.Valid @RequestBody SolutionTieredReviewService.Selection selection) { return success(service.revise(selection)); }
}
