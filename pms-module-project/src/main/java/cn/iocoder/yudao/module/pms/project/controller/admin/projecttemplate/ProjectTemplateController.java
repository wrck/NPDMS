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

/**
 * 管理后台 - PMS 项目模板 Controller（F-PM03 / PM-03）。
 * <p>
 * 供给端模板基座：身份维护、草稿内容编辑、发布校验与版本冻结、四维匹配预演。
 * 路径前缀 {@code /pms/project-templates}（复数，SDS 10-api 契约），
 * 对应菜单权限 {@code pms:project-template:*}（V52 菜单 18060~18066）。
 * 消费端（项目创建实例化）属 F-PM01，不在本 Controller 范围。
 */
@Tag(name = "管理后台 - PMS 项目模板")
@RestController
@RequestMapping("/pms/project-templates")
@Validated
public class ProjectTemplateController {

    @Resource
    private ProjectTemplateService projectTemplateService;

    @GetMapping("/page")
    @Operation(summary = "分页查询项目模板（状态/编码/名称过滤）")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<PageResult<ProjectTemplateRespVO>> getProjectTemplatePage(
            @Valid ProjectTemplatePageReqVO pageReqVO) {
        PageResult<ProjectTemplateDO> pageResult = projectTemplateService.getProjectTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectTemplateRespVO.class));
    }

    @PostMapping
    @Operation(summary = "创建项目模板（同时生成 DRAFT 草稿版本）")
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Long> createProjectTemplate(@Valid @RequestBody ProjectTemplateCreateReqVO createReqVO) {
        ProjectTemplateDO template = BeanUtils.toBean(createReqVO, ProjectTemplateDO.class);
        return success(projectTemplateService.createProjectTemplate(template));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑模板身份与草稿内容（仅 DRAFT 可编辑，编码不可修改）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Boolean> updateProjectTemplate(@PathVariable("id") Long id,
                                                       @Valid @RequestBody ProjectTemplateUpdateReqVO updateReqVO) {
        // 身份字段编辑（名称/优先级/描述；RETIRED 模板身份冻结）
        projectTemplateService.updateProjectTemplateIdentity(id, updateReqVO.getName(),
                updateReqVO.getMatchPriority(), updateReqVO.getDescription());
        // 草稿内容编辑（整体替换；已发布版本只读，BR-3）
        if (updateReqVO.getContent() != null) {
            projectTemplateService.updateProjectTemplateDraftContent(id, updateReqVO.getContent());
        }
        return success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板（仅无已发布版本且非系统保留）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:delete')")
    public CommonResult<Boolean> deleteProjectTemplate(@PathVariable("id") Long id) {
        projectTemplateService.deleteProjectTemplate(id);
        return success(true);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情（含当前草稿内容与版本清单）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateDetailRespVO> getProjectTemplate(@PathVariable("id") Long id) {
        ProjectTemplateDO template = projectTemplateService.getProjectTemplate(id);
        if (template == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        ProjectTemplateDetailRespVO detail = BeanUtils.toBean(template, ProjectTemplateDetailRespVO.class);
        // 版本清单（版本号倒序，最新 PUBLISHED 在首）
        List<ProjectTemplateRevisionDO> revisions = projectTemplateService.getRevisionList(id);
        detail.setRevisions(BeanUtils.toBean(revisions, ProjectTemplateRevisionRespVO.class));
        // 草稿内容（版本清单含 DRAFT 行才存在草稿）
        boolean hasDraft = revisions.stream()
                .anyMatch(revision -> TemplateRules.REVISION_STATUS_DRAFT.equals(revision.getStatus()));
        detail.setDraftContent(hasDraft ? projectTemplateService.getDraftContent(id) : null);
        return success(detail);
    }

    @PostMapping("/{id}/actions/publish")
    @Operation(summary = "发布模板（校验→冻结版本→转 ACTIVE）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:publish')")
    public CommonResult<Boolean> publishProjectTemplate(@PathVariable("id") Long id) {
        projectTemplateService.publishProjectTemplate(id);
        return success(true);
    }

    @PostMapping("/{id}/actions/disable")
    @Operation(summary = "停用模板（RETIRED，只阻新项目匹配）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:disable')")
    public CommonResult<Boolean> disableProjectTemplate(@PathVariable("id") Long id) {
        projectTemplateService.disableProjectTemplate(id);
        return success(true);
    }

    @GetMapping("/{id}/revisions/{revisionNo}")
    @Operation(summary = "查询已发布版本详情（只读快照）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @Parameter(name = "revisionNo", description = "版本号（正整数，已发布）", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateRevisionDetailRespVO> getProjectTemplateRevision(
            @PathVariable("id") Long id, @PathVariable("revisionNo") Integer revisionNo) {
        // 内容读取内置版本存在性校验（不存在抛 PROJECT_TEMPLATE_NOT_EXISTS）
        TemplateDefinitionContent content = projectTemplateService.getRevisionContent(id, revisionNo);
        ProjectTemplateRevisionDO revision = projectTemplateService.getRevision(id, revisionNo);
        ProjectTemplateRevisionDetailRespVO detail =
                BeanUtils.toBean(revision, ProjectTemplateRevisionDetailRespVO.class);
        detail.setContent(content);
        return success(detail);
    }

    @PostMapping("/actions/match-preview")
    @Operation(summary = "四维匹配预演（唯一命中或冲突清单，不静默选模）")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateMatchRespVO> matchPreview(
            @Valid @RequestBody ProjectTemplateMatchPreviewReqVO reqVO) {
        TemplateMatchResult result = projectTemplateService.matchPreview(
                reqVO.getSigningMethod(), reqVO.getProjectCategory(),
                reqVO.getImplementationMethod(), reqVO.getMajorProjectLevel());
        ProjectTemplateMatchRespVO respVO = new ProjectTemplateMatchRespVO();
        respVO.setOutcome(result.getOutcome() == null ? null : result.getOutcome().name());
        respVO.setMatched(result.getMatched());
        respVO.setConflicts(result.getConflicts());
        return success(respVO);
    }
}
