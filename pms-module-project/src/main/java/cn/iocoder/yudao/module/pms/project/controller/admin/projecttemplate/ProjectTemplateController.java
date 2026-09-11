package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateDetailRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateMatchPreviewReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateMatchRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateRevisionDetailRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateRevisionRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateUpdateReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_EXISTS;

/** PM-03 single project-template resource. V2 authoring uses DesignerDocument. */
@Tag(name = "管理后台 - PMS 项目模板")
@RestController
@RequestMapping({"/api/v1/pms/project-templates", "/pms/project-templates"})
@Validated
public class ProjectTemplateController {

    @Resource
    private ProjectTemplateService projectTemplateService;

    @Resource
    private cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry taskBusinessProviderRegistry;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<PageResult<ProjectTemplateRespVO>> getProjectTemplatePage(@Valid ProjectTemplatePageReqVO pageReqVO) {
        return success(BeanUtils.toBean(projectTemplateService.getProjectTemplatePage(pageReqVO), ProjectTemplateRespVO.class));
    }

    @PostMapping
    @Operation(summary = "创建项目模板（同时生成DRAFT）")
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Long> createProjectTemplate(@Valid @RequestBody ProjectTemplateCreateReqVO createReqVO) {
        return success(projectTemplateService.createProjectTemplate(BeanUtils.toBean(createReqVO, ProjectTemplateDO.class)));
    }

    /** Legacy combined update remains a compatibility adapter; new UI saves Designer through /draft. */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    @Operation(summary = "编辑模板身份；content仅作旧客户端兼容")
    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Boolean> updateProjectTemplate(@PathVariable("id") Long id,
                                                       @Valid @RequestBody ProjectTemplateUpdateReqVO updateReqVO) {
        projectTemplateService.updateProjectTemplateIdentity(id, updateReqVO.getName(),
                updateReqVO.getMatchPriority(), updateReqVO.getDescription());
        if (updateReqVO.getContent() != null) {
            projectTemplateService.updateProjectTemplateDraftContent(id, updateReqVO.getContent());
        }
        return success(true);
    }

    @GetMapping("/{id}/draft")
    @Operation(summary = "读取V2模板设计草稿")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<TemplateDesignerDocument> getDesignerDraft(@PathVariable("id") Long id) {
        return success(projectTemplateService.getDraftDesigner(id));
    }

    @PutMapping("/{id}/draft")
    @Operation(summary = "保存V2模板设计草稿")
    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Boolean> updateDesignerDraft(@PathVariable("id") Long id,
                                                      @RequestBody TemplateDesignerDocument designer) {
        projectTemplateService.updateProjectTemplateDesigner(id, designer);
        return success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板（仅无已发布版本且非系统保留）")
    @PreAuthorize("@ss.hasPermission('pms:project-template:delete')")
    public CommonResult<Boolean> deleteProjectTemplate(@PathVariable("id") Long id) {
        projectTemplateService.deleteProjectTemplate(id);
        return success(true);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateDetailRespVO> getProjectTemplate(@PathVariable("id") Long id) {
        ProjectTemplateDO template = projectTemplateService.getProjectTemplate(id);
        if (template == null) throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        ProjectTemplateDetailRespVO detail = BeanUtils.toBean(template, ProjectTemplateDetailRespVO.class);
        List<ProjectTemplateRevisionDO> revisions = projectTemplateService.getRevisionList(id);
        detail.setRevisions(BeanUtils.toBean(revisions, ProjectTemplateRevisionRespVO.class));
        boolean hasDraft = revisions.stream().anyMatch(revision -> TemplateRules.REVISION_STATUS_DRAFT.equals(revision.getStatus()));
        detail.setDraftContent(hasDraft ? projectTemplateService.getDraftContent(id) : null);
        return success(detail);
    }

    @PostMapping("/{id}/actions/validate")
    @Operation(summary = "Compiler dry-run校验")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Validation>
            validateProjectTemplate(@PathVariable("id") Long id) {
        return success(projectTemplateService.validateProjectTemplate(id));
    }

    @PostMapping("/{id}/actions/copy")
    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Long> copyProjectTemplate(@PathVariable("id") Long id,
            @Valid @RequestBody cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCopyReqVO body,
            @org.springframework.web.bind.annotation.RequestHeader("If-Match") String version,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key) {
        return success(projectTemplateService.copyProjectTemplate(id,
                cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands.version(version), body, key));
    }

    @PostMapping("/{id}/actions/publish")
    @Operation(summary = "Compiler编译并发布不可变ExecutionSnapshot")
    @PreAuthorize("@ss.hasPermission('pms:project-template:publish')")
    public CommonResult<Boolean> publishProjectTemplate(@PathVariable("id") Long id) {
        projectTemplateService.publishProjectTemplate(id);
        return success(true);
    }

    @PostMapping("/{id}/actions/disable")
    @Operation(summary = "停用模板，只阻新项目匹配")
    @PreAuthorize("@ss.hasPermission('pms:project-template:disable')")
    public CommonResult<Boolean> disableProjectTemplate(@PathVariable("id") Long id) {
        projectTemplateService.disableProjectTemplate(id);
        return success(true);
    }

    @GetMapping("/{id}/revisions/{revisionNo}")
    @Operation(summary = "查询已发布版本兼容详情")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateRevisionDetailRespVO> getProjectTemplateRevision(
            @PathVariable("id") Long id, @PathVariable("revisionNo") Integer revisionNo) {
        TemplateDefinitionContent content = projectTemplateService.getRevisionContent(id, revisionNo);
        ProjectTemplateRevisionDO revision = projectTemplateService.getRevision(id, revisionNo);
        ProjectTemplateRevisionDetailRespVO detail = BeanUtils.toBean(revision, ProjectTemplateRevisionDetailRespVO.class);
        detail.setContent(content);
        return success(detail);
    }

    @PostMapping("/actions/match-preview")
    @Operation(summary = "四维匹配预演")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateMatchRespVO> matchPreview(@Valid @RequestBody ProjectTemplateMatchPreviewReqVO reqVO) {
        TemplateMatchResult result = projectTemplateService.matchPreview(reqVO.getSigningMethod(), reqVO.getProjectCategory(),
                reqVO.getImplementationMethod(), reqVO.getMajorProjectLevel());
        ProjectTemplateMatchRespVO respVO = new ProjectTemplateMatchRespVO();
        respVO.setOutcome(result.getOutcome() == null ? null : result.getOutcome().name());
        respVO.setMatched(result.getMatched());
        respVO.setConflicts(result.getConflicts());
        return success(respVO);
    }

    @GetMapping("/actions/completion-fact-catalog")
    @Operation(summary = "完成事实目录")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<List<cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry.CompletionFactCatalogEntry>>
            completionFactCatalog() {
        return success(taskBusinessProviderRegistry.completionFactCatalog());
    }
}
