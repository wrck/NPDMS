package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionPublicSubmissionApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResponseSubmissionService;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionPublicResponseSubmitReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionGrantFileUploadCompleteReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionGrantFileUploadInitReqVO;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitialized;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-questionnaires")
@RequiredArgsConstructor
public class SatisfactionQuestionnairePublicController {
    private static final long MAX_FILE_BYTES = 52_428_800L;
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
                file.getRole(), file.getFileSlotKey(), file.getSequence(), file.getArtifactId(), file.getVersionNo(),
                file.getReferenceKey(), file.getArtifactVersion(), file.getReferenceVersion(),
                file.getAvailabilityVersion(), file.getScopeVersion(), file.getSha256())).toList();
        return success(submissionService.submit(new SatisfactionPublicSubmissionApplicationService.Command(
                TenantContextHolder.getRequiredTenantId(), token, request.getRequestId(), request.getResponseId(),
                request.getCustomerContactRef(), request.getAnswerSnapshot(), files)));
    }

    @PostMapping("/{token}/files/initialize")
    @PermitAll
    public CommonResult<BusinessGrantUploadInitialized> initializeFile(
            @PathVariable("token") String token,
            @Valid @RequestBody SatisfactionGrantFileUploadInitReqVO request) {
        return success(submissionService.initializeFile(
                new SatisfactionPublicSubmissionApplicationService.InitializeFileCommand(
                        TenantContextHolder.getRequiredTenantId(), token, request.getRequestId(),
                        request.getPolicyKey(), request.getOperationId(), request.getFileName(),
                        request.getCategoryCode(), request.getDeclaredSizeBytes(),
                        request.getDeclaredMediaType(), request.getClientSha256())));
    }

    @PostMapping(path = "/{token}/files/{sessionId}/complete", consumes = "multipart/form-data")
    @PermitAll
    public CommonResult<BusinessGrantFileFact> completeFile(
            @PathVariable("token") String token, @PathVariable("sessionId") Long sessionId,
            @Valid @RequestPart("metadata") SatisfactionGrantFileUploadCompleteReqVO request,
            @RequestPart("file") MultipartFile file) throws java.io.IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("SATISFACTION_GRANT_FILE_CONTENT_INVALID");
        }
        return success(submissionService.completeFile(
                new SatisfactionPublicSubmissionApplicationService.CompleteFileCommand(
                        TenantContextHolder.getRequiredTenantId(), token, request.getRequestId(),
                        request.getResponseId(), request.getPolicyKey(), request.getOperationId(),
                        request.getFileSlotKey(), request.getFileSequence(), request.getArtifactId(),
                        sessionId, file.getBytes(), request.getClientSha256())));
    }
}
