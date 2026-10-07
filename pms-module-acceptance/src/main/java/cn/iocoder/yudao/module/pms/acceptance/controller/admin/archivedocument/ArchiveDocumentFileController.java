package cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/archive-documents")
public class ArchiveDocumentFileController {
    private final NativeAcceptanceDeliveryAccess owners;
    private final NativeGeneratedFileApi files;
    @GetMapping("/{id}/files/{materialId}")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:query')")
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<String> download(@PathVariable Long id,@PathVariable Long materialId) {
        owners.require(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),
                "archiveDocument",String.valueOf(id),"ARCHIVE_DOCUMENT",false,true,null);
        return CommonResult.success(files.requestDownload("ACC","archiveDocument",id,materialId));
    }
}
