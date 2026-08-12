package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.service.projecttask.ProjectTaskService;
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

@Tag(name = "管理后台 - PMS 项目任务 WBS")
@RestController
@RequestMapping("/pms/project-task")
@Validated
public class ProjectTaskController {

    @Resource
    private ProjectTaskService projectTaskService;

    @PostMapping("/create")
    @Operation(summary = "创建项目任务")
    @PreAuthorize("@ss.hasPermission('pms:project-task:create')")
    public CommonResult<Long> createProjectTask(@Valid @RequestBody ProjectTaskSaveReqVO createReqVO) {
        return success(projectTaskService.createProjectTask(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目任务")
    @PreAuthorize("@ss.hasPermission('pms:project-task:update')")
    public CommonResult<Boolean> updateProjectTask(@Valid @RequestBody ProjectTaskSaveReqVO updateReqVO) {
        projectTaskService.updateProjectTask(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-task:delete')")
    public CommonResult<Boolean> deleteProjectTask(@RequestParam("id") Long id) {
        projectTaskService.deleteProjectTask(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<ProjectTaskRespVO> getProjectTask(@RequestParam("id") Long id) {
        ProjectTaskDO task = projectTaskService.getProjectTask(id);
        return success(BeanUtils.toBean(task, ProjectTaskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目任务分页")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<PageResult<ProjectTaskRespVO>> getProjectTaskPage(@Validated ProjectTaskPageReqVO pageReqVO) {
        PageResult<ProjectTaskDO> pageResult = projectTaskService.getProjectTaskPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectTaskRespVO.class));
    }

    @GetMapping("/tree")
    @Operation(summary = "获取项目任务树")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<List<ProjectTaskTreeRespVO>> getProjectTaskTree(@RequestParam("projectId") Long projectId) {
        return success(projectTaskService.getProjectTaskTree(projectId));
    }

    @GetMapping("/descendants")
    @Operation(summary = "获取任务后代列表")
    @Parameter(name = "taskId", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<List<ProjectTaskRespVO>> getProjectTaskDescendants(@RequestParam("taskId") Long taskId) {
        List<ProjectTaskDO> list = projectTaskService.getProjectTaskDescendants(taskId);
        return success(BeanUtils.toBean(list, ProjectTaskRespVO.class));
    }

    @PutMapping("/move")
    @Operation(summary = "移动项目任务")
    @PreAuthorize("@ss.hasPermission('pms:project-task:update')")
    public CommonResult<Boolean> moveProjectTask(@Valid @RequestBody ProjectTaskMoveReqVO reqVO) {
        projectTaskService.moveProjectTask(reqVO);
        return success(true);
    }

}
