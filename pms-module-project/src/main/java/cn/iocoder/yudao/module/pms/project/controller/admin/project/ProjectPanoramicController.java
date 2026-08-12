package cn.iocoder.yudao.module.pms.project.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPanoramicRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectProgressRespVO;
import cn.iocoder.yudao.module.pms.project.service.project.ProjectPanoramicService;
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
 * 管理后台 - PMS 项目全景 Controller（FR-PROJ-011 / FR-PROJ-021 / FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 路径前缀 {@code /pms/project-panoramic}，对应菜单权限 {@code pms:project-panoramic:query}。
 * 提供项目全景聚合与项目进度查询。
 */
@Tag(name = "管理后台 - PMS 项目全景")
@RestController
@RequestMapping("/pms/project-panoramic")
@Validated
public class ProjectPanoramicController {

    @Resource
    private ProjectPanoramicService projectPanoramicService;

    @GetMapping("/panoramic")
    @Operation(summary = "查询项目全景")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-panoramic:query')")
    public CommonResult<ProjectPanoramicRespVO> getProjectPanoramic(
            @RequestParam("projectId") Long projectId) {
        return success(projectPanoramicService.getProjectPanoramic(projectId));
    }

    @GetMapping("/progress")
    @Operation(summary = "查询项目进度")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-panoramic:query')")
    public CommonResult<ProjectProgressRespVO> getProjectProgress(
            @RequestParam("projectId") Long projectId) {
        return success(projectPanoramicService.getProjectProgress(projectId));
    }
}
