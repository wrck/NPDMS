package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionPublicSubmissionApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResponseSubmissionService;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionPublicResponseSubmitReqVO;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-questionnaires")
@RequiredArgsConstructor
public class SatisfactionQuestionnairePublicController {
    private final SatisfactionAccessGrantService grantService;
    private final SatisfactionPublicSubmissionApplicationService submissionService;

    @GetMapping("/{token}")
    @PermitAll
    public CommonResult<SatisfactionAccessGrantService.PublicQuestionnaire> inspect(
            @PathVariable("token") String token) {
        return success(grantService.inspect(TenantContextHolder.getRequiredTenantId(), token));
    }

    @PostMapping("/{token}/responses")
    @PermitAll
    public CommonResult<SatisfactionPublicSubmissionApplicationService.SubmissionOutcome> submit(
            @PathVariable("token") String token,
            @Valid @RequestBody SatisfactionPublicResponseSubmitReqVO request) {
        var files = request.getFiles().stream().map(file -> new SatisfactionResponseSubmissionService.FileFact(
                file.getRole(), file.getSequence(), file.getArtifactId(), file.getVersionNo(),
                file.getReferenceKey(), file.getArtifactVersion(), file.getReferenceVersion(),
                file.getAvailabilityVersion(), file.getScopeVersion(), file.getSha256())).toList();
        return success(submissionService.submit(new SatisfactionPublicSubmissionApplicationService.Command(
                TenantContextHolder.getRequiredTenantId(), token, request.getRequestId(),
                request.getCustomerContactRef(), request.getAnswerSnapshot(), files)));
    }
}
