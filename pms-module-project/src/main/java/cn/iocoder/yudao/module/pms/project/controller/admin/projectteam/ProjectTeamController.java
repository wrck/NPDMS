package cn.iocoder.yudao.module.pms.project.controller.admin.projectteam;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import cn.iocoder.yudao.module.pms.project.service.projectteam.ProjectTeamService;
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

@Tag(name = "管理后台 - PMS 项目团队")
@RestController
@RequestMapping("/pms/project-team")
@Validated
public class ProjectTeamController {

    @Resource
    private ProjectTeamService projectTeamService;

    @PostMapping("/create")
    @Operation(summary = "创建项目团队成员")
    @PreAuthorize("@ss.hasPermission('pms:project-team:create')")
    public CommonResult<Long> createProjectTeamMember(@Valid @RequestBody ProjectTeamMemberSaveReqVO createReqVO) {
        return success(projectTeamService.createProjectTeamMember(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目团队成员")
    @PreAuthorize("@ss.hasPermission('pms:project-team:create')")
    public CommonResult<Boolean> updateProjectTeamMember(@Valid @RequestBody ProjectTeamMemberSaveReqVO updateReqVO) {
        projectTeamService.updateProjectTeamMember(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目团队成员")
    @Parameter(name = "id", description = "团队成员编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-team:create')")
    public CommonResult<Boolean> deleteProjectTeamMember(@RequestParam("id") Long id) {
        projectTeamService.deleteProjectTeamMember(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目团队成员")
    @Parameter(name = "id", description = "团队成员编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-team:query')")
    public CommonResult<ProjectTeamMemberRespVO> getProjectTeamMember(@RequestParam("id") Long id) {
        ProjectTeamMemberDO member = projectTeamService.getProjectTeamMember(id);
        return success(BeanUtils.toBean(member, ProjectTeamMemberRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目团队成员分页")
    @PreAuthorize("@ss.hasPermission('pms:project-team:query')")
    public CommonResult<PageResult<ProjectTeamMemberRespVO>> getProjectTeamMemberPage(
            @Validated ProjectTeamMemberPageReqVO pageReqVO) {
        PageResult<ProjectTeamMemberDO> pageResult = projectTeamService.getProjectTeamMemberPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectTeamMemberRespVO.class));
    }

    @GetMapping("/list-by-project")
    @Operation(summary = "根据项目编号获取团队成员列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('pms:project-team:query')")
    public CommonResult<List<ProjectTeamMemberRespVO>> getProjectTeamListByProjectId(
            @RequestParam("projectId") Long projectId) {
        List<ProjectTeamMemberDO> list = projectTeamService.getTeamListByProjectId(projectId);
        return success(BeanUtils.toBean(list, ProjectTeamMemberRespVO.class));
    }

}
