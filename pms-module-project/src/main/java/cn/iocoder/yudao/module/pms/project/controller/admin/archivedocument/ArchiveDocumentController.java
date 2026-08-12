package cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.project.service.archivedocument.ArchiveDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 归档文档")
@RestController
@RequestMapping("/pms/acc-archive-document")
@Validated
public class ArchiveDocumentController {

    @Resource
    private ArchiveDocumentService archiveDocumentService;

    @PostMapping("/create")
    @Operation(summary = "创建归档文档")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:create')")
    public CommonResult<Long> create(@Valid @RequestBody ArchiveDocumentSaveReqVO createReqVO) {
        return success(archiveDocumentService.createArchiveDocument(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新归档文档")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ArchiveDocumentSaveReqVO updateReqVO) {
        archiveDocumentService.updateArchiveDocument(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除归档文档")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        archiveDocumentService.deleteArchiveDocument(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得归档文档分页")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:query')")
    public CommonResult<PageResult<ArchiveDocumentRespVO>> getPage(@Validated ArchiveDocumentPageReqVO pageReqVO) {
        PageResult<ArchiveDocumentDO> pageResult = archiveDocumentService.getArchiveDocumentPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ArchiveDocumentRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得归档文档")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:query')")
    public CommonResult<ArchiveDocumentRespVO> get(@RequestParam("id") Long id) {
        ArchiveDocumentDO entity = archiveDocumentService.getArchiveDocument(id);
        return success(BeanUtils.toBean(entity, ArchiveDocumentRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交归档文档（0草稿 → 1待归档）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        archiveDocumentService.submitArchiveDocument(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档文档（1待归档 → 2已归档，归档后版本不可覆盖）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-archive-document:audit')")
    public CommonResult<Boolean> archive(@RequestParam("id") Long id) {
        archiveDocumentService.archiveArchiveDocument(id);
        return success(true);
    }

}
