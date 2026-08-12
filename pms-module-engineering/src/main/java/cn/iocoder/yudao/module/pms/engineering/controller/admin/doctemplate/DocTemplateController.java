package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateSelectReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateVersionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateVersionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateVersionDO;
import cn.iocoder.yudao.module.pms.engineering.service.doctemplate.DocTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 工程文档模板 Controller（V36 结构化文档模板）。
 * <p>
 * 路径前缀 {@code /pms/eng-doc-template}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-doc-template:*}。
 * <p>
 * 提供模板主表 CRUD、状态流转、版本管理（创建/查询/发布）与模板选择（三级降级匹配）能力。
 */
@Tag(name = "管理后台 - PMS 工程文档模板")
@RestController
@RequestMapping("/pms/eng-doc-template")
@Validated
public class DocTemplateController {

    @Resource
    private DocTemplateService docTemplateService;

    // ==================== 模板主表 ====================

    @PostMapping("/create")
    @Operation(summary = "创建文档模板")
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:create')")
    public CommonResult<Long> createDocTemplate(@Valid @RequestBody DocTemplateSaveReqVO createReqVO) {
        return success(docTemplateService.createDocTemplate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新文档模板")
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:update')")
    public CommonResult<Boolean> updateDocTemplate(@Valid @RequestBody DocTemplateSaveReqVO updateReqVO) {
        docTemplateService.updateDocTemplate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文档模板")
    @Parameter(name = "id", description = "模板ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:delete')")
    public CommonResult<Boolean> deleteDocTemplate(@RequestParam("id") Long id) {
        docTemplateService.deleteDocTemplate(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询文档模板详情")
    @Parameter(name = "id", description = "模板ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<DocTemplateRespVO> getDocTemplate(@RequestParam("id") Long id) {
        DocTemplateDO entity = docTemplateService.getDocTemplate(id);
        return success(BeanUtils.toBean(entity, DocTemplateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询文档模板")
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<PageResult<DocTemplateRespVO>> getDocTemplatePage(@Validated DocTemplatePageReqVO pageReqVO) {
        PageResult<DocTemplateDO> pageResult = docTemplateService.getDocTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DocTemplateRespVO.class));
    }

    @PutMapping("/publish")
    @Operation(summary = "发布文档模板（0 草稿/2 已停用 → 1 已发布，需有已发布版本）")
    @Parameter(name = "id", description = "模板ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:publish')")
    public CommonResult<Boolean> publishDocTemplate(@RequestParam("id") Long id) {
        docTemplateService.publishDocTemplate(id);
        return success(true);
    }

    @PutMapping("/disable")
    @Operation(summary = "停用文档模板（1 已发布 → 2 已停用）")
    @Parameter(name = "id", description = "模板ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:publish')")
    public CommonResult<Boolean> disableDocTemplate(@RequestParam("id") Long id) {
        docTemplateService.disableDocTemplate(id);
        return success(true);
    }

    @GetMapping("/published-list")
    @Operation(summary = "查询已发布模板列表（供文档创建时下拉选择）")
    @Parameter(name = "docCategory", description = "文档类别（可空）")
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<List<DocTemplateRespVO>> getPublishedDocTemplateList(
            @RequestParam(value = "docCategory", required = false) String docCategory) {
        List<DocTemplateDO> list = docTemplateService.getPublishedDocTemplateList(docCategory);
        return success(BeanUtils.toBean(list, DocTemplateRespVO.class));
    }

    // ==================== 模板版本 ====================

    @PostMapping("/version/create")
    @Operation(summary = "创建模板新版本")
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:update')")
    public CommonResult<Long> createVersion(@Valid @RequestBody DocTemplateVersionSaveReqVO createReqVO) {
        DocTemplateVersionDO entity = docTemplateService.createVersion(createReqVO);
        return success(entity.getId());
    }

    @GetMapping("/version/get")
    @Operation(summary = "查询版本详情")
    @Parameter(name = "id", description = "版本ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<DocTemplateVersionRespVO> getVersion(@RequestParam("id") Long id) {
        DocTemplateVersionDO entity = docTemplateService.getVersion(id);
        return success(BeanUtils.toBean(entity, DocTemplateVersionRespVO.class));
    }

    @GetMapping("/version/list")
    @Operation(summary = "查询模板的全部版本列表")
    @Parameter(name = "templateId", description = "模板ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<List<DocTemplateVersionRespVO>> getVersionList(
            @RequestParam("templateId") Long templateId) {
        List<DocTemplateVersionDO> list = docTemplateService.getVersionListByTemplateId(templateId);
        return success(BeanUtils.toBean(list, DocTemplateVersionRespVO.class));
    }

    @GetMapping("/version/published")
    @Operation(summary = "查询模板的已发布版本")
    @Parameter(name = "templateId", description = "模板ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<DocTemplateVersionRespVO> getPublishedVersion(
            @RequestParam("templateId") Long templateId) {
        DocTemplateVersionDO entity = docTemplateService.getPublishedVersion(templateId);
        return success(BeanUtils.toBean(entity, DocTemplateVersionRespVO.class));
    }

    @PutMapping("/version/publish")
    @Operation(summary = "发布版本（设置 published=1，模板 currentVersionId 指向它）")
    @Parameter(name = "id", description = "版本ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:publish')")
    public CommonResult<Boolean> publishVersion(@RequestParam("id") Long id) {
        docTemplateService.publishVersion(id);
        return success(true);
    }

    // ==================== 模板选择与快照 ====================

    @GetMapping("/select")
    @Operation(summary = "按条件筛选匹配的模板（三级降级匹配）")
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<List<DocTemplateRespVO>> selectTemplates(@Validated DocTemplateSelectReqVO reqVO) {
        List<DocTemplateDO> list = docTemplateService.selectTemplates(reqVO);
        return success(BeanUtils.toBean(list, DocTemplateRespVO.class));
    }

    @GetMapping("/snapshot")
    @Operation(summary = "构建模板快照JSON（用于文档创建时锁定模板结构）")
    @Parameter(name = "versionId", description = "版本ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-doc-template:query')")
    public CommonResult<String> buildTemplateSnapshot(@RequestParam("versionId") Long versionId) {
        return success(docTemplateService.buildTemplateSnapshot(versionId));
    }
}
