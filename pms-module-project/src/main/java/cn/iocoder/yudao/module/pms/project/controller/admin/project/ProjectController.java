package cn.iocoder.yudao.module.pms.project.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectAssignManagerReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectClassifyReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.service.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 项目")
@RestController
@RequestMapping("/pms/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<Long> createProject(@Valid @RequestBody ProjectSaveReqVO createReqVO) {
        return success(projectService.createProject(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> updateProject(@Valid @RequestBody ProjectSaveReqVO updateReqVO) {
        projectService.updateProject(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project:delete')")
    public CommonResult<Boolean> deleteProject(@RequestParam("id") Long id) {
        projectService.deleteProject(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectRespVO> getProject(@RequestParam("id") Long id) {
        ProjectDO project = projectService.getProject(id);
        return success(BeanUtils.toBean(project, ProjectRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目分页")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PageResult<ProjectRespVO>> getProjectPage(@Validated ProjectPageReqVO pageReqVO) {
        PageResult<ProjectDO> pageResult = projectService.getProjectPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectRespVO.class));
    }

    @PutMapping("/classify")
    @Operation(summary = "项目分类")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<Boolean> classifyProject(@Valid @RequestBody ProjectClassifyReqVO reqVO) {
        projectService.classifyProject(reqVO);
        return success(true);
    }

    @PutMapping("/assign-manager")
    @Operation(summary = "指派项目经理")
    @PreAuthorize("@ss.hasPermission('pms:project:assign')")
    public CommonResult<Boolean> assignProjectManager(@Valid @RequestBody ProjectAssignManagerReqVO reqVO) {
        projectService.assignProjectManager(reqVO);
        return success(true);
    }

}
