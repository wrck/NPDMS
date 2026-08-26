package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.service.projecttask.ProjectTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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

    @GetMapping("/tree")
    @Operation(summary = "获取项目任务树")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<List<ProjectTaskTreeRespVO>> getProjectTaskTree(@RequestParam("projectId") Long projectId) {
        return success(projectTaskService.getProjectTaskTree(projectId));
    }

}
