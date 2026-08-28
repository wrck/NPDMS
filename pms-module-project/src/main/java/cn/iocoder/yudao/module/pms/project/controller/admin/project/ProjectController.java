package cn.iocoder.yudao.module.pms.project.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.service.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 项目（旧链只读过渡，F-PM01 存量冻结）
 * <p>
 * 写端点（create/update/delete/classify/assign-manager）已随 F-PM01 退役：
 * 新链复数路由 {@code /pms/projects} 承接全部写语义（ProjectMasterController）。
 * 本 Controller 仅保留 get/page 只读端点，供约 30 处旧页面选择器过渡消费；
 * 旧 {@code pms_project} 数据冻结只读，待 AI-MIG-000 迁移。
 */
@Tag(name = "管理后台 - PMS 项目（只读过渡）")
@RestController
@RequestMapping("/pms/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @GetMapping("/get")
    @Operation(summary = "获得项目（只读）")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectRespVO> getProject(@RequestParam("id") Long id) {
        ProjectDO project = projectService.getProject(id);
        return success(BeanUtils.toBean(project, ProjectRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目分页（只读）")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PageResult<ProjectRespVO>> getProjectPage(@Validated ProjectPageReqVO pageReqVO) {
        PageResult<ProjectDO> pageResult = projectService.getProjectPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectRespVO.class));
    }

}
