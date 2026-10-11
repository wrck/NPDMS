package cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.*;
import cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate.CompletionConfirmationWorkflowService;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/completion-confirmations")
public class CompletionConfirmationWorkflowController {
    private final CompletionConfirmationWorkflowService service;
    private final NativeGeneratedFileApi files;
    private Long tenant(){return TenantContextHolder.getRequiredTenantId();}
    private Long actor(){return SecurityFrameworkUtils.getLoginUserId();}
    @GetMapping @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:query')")
    public CommonResult<PageResult<CompletionCertificateRespVO>> page(@Valid CompletionCertificatePageReqVO query,@RequestParam(value="survey",defaultValue="false") boolean survey){return success(service.page(tenant(),actor(),query,survey));}
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:query')")
    public CommonResult<CompletionConfirmationWorkflowService.Detail> detail(@PathVariable("id") Long id){return success(service.detail(tenant(),actor(),id));}
    @PostMapping @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:create')")
    public CommonResult<Long> create(@Valid @RequestBody CompletionCertificateSaveReqVO body){body.setId(null);return success(service.save(tenant(),actor(),body));}
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:update')")
    public CommonResult<Long> update(@PathVariable("id") Long id,@Valid @RequestBody CompletionCertificateSaveReqVO body){body.setId(id);return success(service.save(tenant(),actor(),body));}
    @PostMapping("/{id}/actions/issue") @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:submit')")
    public CommonResult<CompletionConfirmationWorkflowService.Issue> issue(@PathVariable("id") Long id){return success(service.issue(tenant(),actor(),id));}
    @PostMapping("/{id}/actions/void") @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:update')")
    public CommonResult<Boolean> voidRecord(@PathVariable("id") Long id){service.voidRecord(tenant(),actor(),id);return success(true);}
    @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:delete')")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id){service.delete(tenant(),actor(),id);return success(true);}
    @PostMapping(path="/{id}/documents",consumes="multipart/form-data") @PreAuthorize("@ss.hasPermission('pms:file:upload')")
    public CommonResult<NativeGeneratedFileApi.RegisteredFile> document(@PathVariable("id") Long id,@RequestParam("expectedVersion") Long version,
            @RequestParam("kind") String kind,@RequestHeader("Idempotency-Key") String key,@RequestPart("file") MultipartFile file) throws java.io.IOException{
        if(file.getSize()>52_428_800)throw new IllegalArgumentException("PDF文件过大");
        return success(service.savePdf(tenant(),actor(),id,version,kind,key,file.getBytes()));
    }
    @GetMapping("/{id}/files/{materialId}") @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:query')")
    public CommonResult<String> download(@PathVariable("id") Long id,@PathVariable("materialId") Long materialId){
        service.detail(tenant(),actor(),id);return success(files.requestDownload("ACC","completionCertificate",id,materialId));
    }
}
