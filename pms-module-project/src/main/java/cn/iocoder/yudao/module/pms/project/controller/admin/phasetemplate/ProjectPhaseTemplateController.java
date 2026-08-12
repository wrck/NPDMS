package cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phasetemplate.ProjectPhaseTemplateDO;
import cn.iocoder.yudao.module.pms.project.service.phasetemplate.ProjectPhaseTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 阶段模板 Controller（FR-PROJ-015 / T-V1-PROJ-007）。
 * <p>
 * 路径前缀 {@code /pms/phase-template}，对应菜单权限 {@code pms:phase-template:*}。
 */
@Tag(name = "管理后台 - PMS 阶段模板")
@RestController
@RequestMapping("/pms/phase-template")
@Validated
public class ProjectPhaseTemplateController {

    @Resource
    private ProjectPhaseTemplateService projectPhaseTemplateService;

    @PostMapping("/create")
    @Operation(summary = "创建阶段模板")
    @PreAuthorize("@ss.hasPermission('pms:phase-template:create')")
    public CommonResult<Long> createTemplate(@Valid @RequestBody ProjectPhaseTemplateSaveReqVO createReqVO) {
        return success(projectPhaseTemplateService.createProjectPhaseTemplate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新阶段模板")
    @PreAuthorize("@ss.hasPermission('pms:phase-template:create')")
    public CommonResult<Boolean> updateTemplate(@Valid @RequestBody ProjectPhaseTemplateSaveReqVO updateReqVO) {
        projectPhaseTemplateService.updateProjectPhaseTemplate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除阶段模板")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:phase-template:create')")
    public CommonResult<Boolean> deleteTemplate(@RequestParam("id") Long id) {
        projectPhaseTemplateService.deleteProjectPhaseTemplate(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除阶段模板")
    @Parameter(name = "ids", description = "模板编号列表，逗号分隔", required = true)
    @PreAuthorize("@ss.hasPermission('pms:phase-template:create')")
    public CommonResult<Boolean> deleteTemplateList(@RequestParam("ids") Collection<Long> ids) {
        projectPhaseTemplateService.deleteProjectPhaseTemplateList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询阶段模板详情")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:phase-template:query')")
    public CommonResult<ProjectPhaseTemplateRespVO> getTemplate(@RequestParam("id") Long id) {
        ProjectPhaseTemplateDO template = projectPhaseTemplateService.getProjectPhaseTemplate(id);
        return success(BeanUtils.toBean(template, ProjectPhaseTemplateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询阶段模板")
    @PreAuthorize("@ss.hasPermission('pms:phase-template:query')")
    public CommonResult<PageResult<ProjectPhaseTemplateRespVO>> getTemplatePage(@Validated ProjectPhaseTemplatePageReqVO pageReqVO) {
        PageResult<ProjectPhaseTemplateDO> pageResult = projectPhaseTemplateService.getProjectPhaseTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectPhaseTemplateRespVO.class));
    }

    @GetMapping("/enabled-list")
    @Operation(summary = "查询全部启用阶段模板")
    @PreAuthorize("@ss.hasPermission('pms:phase-template:query')")
    public CommonResult<List<ProjectPhaseTemplateRespVO>> getEnabledTemplateList() {
        List<ProjectPhaseTemplateDO> list = projectPhaseTemplateService.getEnabledProjectPhaseTemplateList();
        return success(BeanUtils.toBean(list, ProjectPhaseTemplateRespVO.class));
    }

    @GetMapping("/enabled-list-by-type")
    @Operation(summary = "按项目类型查询启用阶段模板")
    @Parameter(name = "projectType", description = "项目类型", required = true)
    @PreAuthorize("@ss.hasPermission('pms:phase-template:query')")
    public CommonResult<List<ProjectPhaseTemplateRespVO>> getEnabledTemplateListByType(
            @RequestParam("projectType") String projectType) {
        List<ProjectPhaseTemplateDO> list = projectPhaseTemplateService.getEnabledProjectPhaseTemplateListByType(projectType);
        return success(BeanUtils.toBean(list, ProjectPhaseTemplateRespVO.class));
    }
}
