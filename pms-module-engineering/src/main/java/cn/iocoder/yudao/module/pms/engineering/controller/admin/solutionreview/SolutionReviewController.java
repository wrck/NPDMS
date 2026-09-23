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
    @GetMapping("/policy") @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewPolicyService.Policy> policy(@RequestParam Long projectId) {
        return success(service.policy(projectId));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<Object> invalid(IllegalArgumentException error) { return CommonResult.error(400,error.getMessage()); }
    @GetMapping("/definition") @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<SolutionReviewBpmApi.Definition> definition() { return success(bpm.definition(TenantContextHolder.getRequiredTenantId())); }
    @GetMapping("/source") @PreAuthorize("@ss.hasPermission('pms:sol-solution:query')")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionRespVO> source(@RequestParam String businessKey) {
        return success(cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(service.sourceForReview(businessKey),
                cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionRespVO.class));
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
