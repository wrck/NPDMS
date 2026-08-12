package cn.iocoder.yudao.module.pms.project.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;
import cn.iocoder.yudao.module.pms.project.service.risk.ProjectRiskService;
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
 * 管理后台 - PMS 项目风险 Controller（FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 路径前缀 {@code /pms/project-risk}，对应菜单权限 {@code pms:project-risk:*}。
 */
@Tag(name = "管理后台 - PMS 项目风险")
@RestController
@RequestMapping("/pms/project-risk")
@Validated
public class ProjectRiskController {

    @Resource
    private ProjectRiskService projectRiskService;

    @PostMapping("/create")
    @Operation(summary = "创建项目风险")
    @PreAuthorize("@ss.hasPermission('pms:project-risk:create')")
    public CommonResult<Long> createRisk(@Valid @RequestBody ProjectRiskSaveReqVO createReqVO) {
        return success(projectRiskService.createRisk(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目风险")
    @PreAuthorize("@ss.hasPermission('pms:project-risk:create')")
    public CommonResult<Boolean> updateRisk(@Valid @RequestBody ProjectRiskSaveReqVO updateReqVO) {
        projectRiskService.updateRisk(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目风险")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-risk:create')")
    public CommonResult<Boolean> deleteRisk(@RequestParam("id") Long id) {
        projectRiskService.deleteRisk(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除项目风险")
    @Parameter(name = "ids", description = "风险编号列表，逗号分隔", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-risk:create')")
    public CommonResult<Boolean> deleteRiskList(@RequestParam("ids") Collection<Long> ids) {
        projectRiskService.deleteRiskList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询风险详情")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-risk:query')")
    public CommonResult<ProjectRiskRespVO> getRisk(@RequestParam("id") Long id) {
        ProjectRiskDO risk = projectRiskService.getRisk(id);
        return success(BeanUtils.toBean(risk, ProjectRiskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询项目风险")
    @PreAuthorize("@ss.hasPermission('pms:project-risk:query')")
    public CommonResult<PageResult<ProjectRiskRespVO>> getRiskPage(@Validated ProjectRiskPageReqVO pageReqVO) {
        PageResult<ProjectRiskDO> pageResult = projectRiskService.getRiskPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectRiskRespVO.class));
    }

    @GetMapping("/list-by-project")
    @Operation(summary = "按项目查询全部风险")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-risk:query')")
    public CommonResult<List<ProjectRiskRespVO>> getRiskListByProjectId(
            @RequestParam("projectId") Long projectId) {
        List<ProjectRiskDO> list = projectRiskService.getRiskListByProjectId(projectId);
        return success(BeanUtils.toBean(list, ProjectRiskRespVO.class));
    }

    @PutMapping("/transition-status")
    @Operation(summary = "切换风险状态")
    @PreAuthorize("@ss.hasPermission('pms:project-risk:create')")
    public CommonResult<Boolean> transitionStatus(@RequestParam("riskId") Long riskId,
                                                  @RequestParam("targetStatus") Integer targetStatus,
                                                  @RequestParam(value = "version", required = false) Integer version) {
        projectRiskService.transitionStatus(riskId, targetStatus, version);
        return success(true);
    }
}
