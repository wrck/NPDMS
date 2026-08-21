package cn.iocoder.yudao.module.pms.project.controller.admin.projecttree;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeNodeRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeService;
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

@Tag(name = "管理后台 - PMS 项目树")
@RestController
@RequestMapping("/pms/project-tree")
@Validated
public class ProjectTreeController {

    @Resource
    private ProjectTreeService projectTreeService;

    @GetMapping("/tree")
    @Operation(summary = "获取项目树")
    @Parameter(name = "rootProjectId", description = "根项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('pms:project-tree:query')")
    public CommonResult<ProjectTreeNodeRespVO> getProjectTree(@RequestParam("rootProjectId") Long rootProjectId) {
        return success(projectTreeService.getProjectTree(rootProjectId));
    }

    @GetMapping("/descendants")
    @Operation(summary = "获取后代项目列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-tree:query')")
    public CommonResult<List<ProjectTreeNodeRespVO>> getDescendants(@RequestParam("projectId") Long projectId) {
        List<ProjectDO> list = projectTreeService.getDescendants(projectId);
        return success(BeanUtils.toBean(list, ProjectTreeNodeRespVO.class));
    }

    @GetMapping("/path")
    @Operation(summary = "获取项目从根到自身的路径")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-tree:query')")
    public CommonResult<List<ProjectTreeNodeRespVO>> getProjectPath(@RequestParam("projectId") Long projectId) {
        List<ProjectDO> list = projectTreeService.getProjectPath(projectId);
        return success(BeanUtils.toBean(list, ProjectTreeNodeRespVO.class));
    }

}
