package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectGovernanceActionDO;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * PMS 项目治理动作 Controller（FR-PROJ-022 / T-V2-PROJ-003）
 * <p>
 * 动作类型：ROLLBACK 回退总部重新指派 / DIRECT_CLOSE 直接关闭
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已执行 → 4已驳回 → 5已撤回
 */
@Tag(name = "管理后台 - PMS 项目治理")
@RestController
@RequestMapping("/pms/project-governance")
@Validated
public class ProjectGovernanceController {

    @Resource
    private ProjectGovernanceService projectGovernanceService;

    @PostMapping("/create")
    @Operation(summary = "创建治理动作")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProjectGovernanceSaveReqVO createReqVO) {
        return success(projectGovernanceService.createGovernanceAction(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新治理动作（仅草稿态）")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProjectGovernanceSaveReqVO updateReqVO) {
        projectGovernanceService.updateGovernanceAction(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除治理动作（仅草稿/已驳回态）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectGovernanceService.deleteGovernanceAction(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得治理动作分页")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:query')")
    public CommonResult<PageResult<ProjectGovernanceRespVO>> getPage(@Validated ProjectGovernancePageReqVO pageReqVO) {
        PageResult<ProjectGovernanceActionDO> pageResult = projectGovernanceService.getGovernanceActionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectGovernanceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得治理动作详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:query')")
    public CommonResult<ProjectGovernanceRespVO> get(@RequestParam("id") Long id) {
        ProjectGovernanceActionDO entity = projectGovernanceService.getGovernanceAction(id);
        return success(BeanUtils.toBean(entity, ProjectGovernanceRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交治理动作（0草稿/4已驳回 → 1已提交）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        projectGovernanceService.submitGovernanceAction(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批执行治理动作（1已提交/2审批中 → 3已执行/4已驳回）")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody ProjectGovernanceApproveReqVO reqVO) {
        projectGovernanceService.approveGovernanceAction(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回治理动作（1已提交/2审批中 → 5已撤回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:submit')")
    public CommonResult<Boolean> withdraw(@RequestParam("id") Long id) {
        projectGovernanceService.withdrawGovernanceAction(id);
        return success(true);
    }

}
