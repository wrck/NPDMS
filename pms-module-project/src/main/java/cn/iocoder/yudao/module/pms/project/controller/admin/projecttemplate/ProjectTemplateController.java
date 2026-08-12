package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectCreateFromTemplateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 项目模板 Controller
 */
@Tag(name = "管理后台 - PMS 项目模板")
@RestController
@RequestMapping("/pms/project-template")
@Validated
public class ProjectTemplateController {

    @Resource
    private ProjectTemplateService projectTemplateService;

    @PostMapping("/create")
    @Operation(summary = "创建项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Long> createTemplate(@Valid @RequestBody ProjectTemplateSaveReqVO createReqVO) {
        return success(projectTemplateService.createProjectTemplate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Boolean> updateTemplate(@Valid @RequestBody ProjectTemplateSaveReqVO updateReqVO) {
        projectTemplateService.updateProjectTemplate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目模板")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Boolean> deleteTemplate(@RequestParam("id") Long id) {
        projectTemplateService.deleteProjectTemplate(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询项目模板详情")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateRespVO> getTemplate(@RequestParam("id") Long id) {
        ProjectTemplateDO template = projectTemplateService.getProjectTemplate(id);
        return success(BeanUtils.toBean(template, ProjectTemplateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<PageResult<ProjectTemplateRespVO>> getTemplatePage(@Validated ProjectTemplatePageReqVO pageReqVO) {
        PageResult<ProjectTemplateDO> pageResult = projectTemplateService.getProjectTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectTemplateRespVO.class));
    }

    @GetMapping("/enabled-list")
    @Operation(summary = "查询全部启用项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<List<ProjectTemplateRespVO>> getEnabledTemplateList() {
        List<ProjectTemplateDO> list = projectTemplateService.getEnabledProjectTemplateList();
        return success(BeanUtils.toBean(list, ProjectTemplateRespVO.class));
    }

    @GetMapping("/enabled-list-by-type")
    @Operation(summary = "按项目类型查询启用项目模板")
    @Parameter(name = "projectType", description = "项目类型", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<List<ProjectTemplateRespVO>> getEnabledTemplateListByType(
            @RequestParam("projectType") String projectType) {
        List<ProjectTemplateDO> list = projectTemplateService.getEnabledProjectTemplateListByType(projectType);
        return success(BeanUtils.toBean(list, ProjectTemplateRespVO.class));
    }

    @PostMapping("/create-project")
    @Operation(summary = "从模板创建项目")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<Long> createProjectFromTemplate(@Valid @RequestBody ProjectCreateFromTemplateReqVO reqVO) {
        return success(projectTemplateService.createProjectFromTemplate(reqVO));
    }
}
