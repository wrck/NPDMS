package cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosurePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosureRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosureSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.service.projectclosure.ProjectClosureService;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ProjectClosureGuardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目闭环审批 Controller
 * <p>
 * 【待确认：遗留问题闭环规则】允许带条件移交，具体移交条件由业务规则补充。
 */
@Tag(name = "管理后台 - 项目闭环")
@RestController
@RequestMapping("/pms/acc-project-closure")
@Validated
public class ProjectClosureController {

    @Resource
    private ProjectClosureService projectClosureService;

    @PostMapping("/create")
    @Operation(summary = "创建项目闭环")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProjectClosureSaveReqVO createReqVO) {
        return success(projectClosureService.createProjectClosure(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目闭环")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProjectClosureSaveReqVO updateReqVO) {
        projectClosureService.updateProjectClosure(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目闭环")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectClosureService.deleteProjectClosure(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目闭环分页")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:query')")
    public CommonResult<PageResult<ProjectClosureRespVO>> getPage(@Validated ProjectClosurePageReqVO pageReqVO) {
        PageResult<ProjectClosureDO> pageResult = projectClosureService.getProjectClosurePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectClosureRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目闭环")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:query')")
    public CommonResult<ProjectClosureRespVO> get(@RequestParam("id") Long id) {
        ProjectClosureDO entity = projectClosureService.getProjectClosure(id);
        return success(BeanUtils.toBean(entity, ProjectClosureRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交项目闭环（0草稿 → 1待审批）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id,
                                        @RequestHeader("If-Match") long expectedTreeVersion) {
        projectClosureService.submitProjectClosure(id, expectedTreeVersion,
                new ProjectClosureGuardService.Actor(TenantContextHolder.getRequiredTenantId(),
                        SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString()));
        return success(true);
    }

    @PutMapping("/start-approve")
    @Operation(summary = "开始审批（1待审批 → 2审批中）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:audit')")
    public CommonResult<Boolean> startApprove(@RequestParam("id") Long id) {
        projectClosureService.startApprove(id);
        return success(true);
    }

    @PutMapping("/pass")
    @Operation(summary = "通过项目闭环（2审批中 → 3已通过，门禁校验）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:audit')")
    public CommonResult<Boolean> pass(@RequestParam("id") Long id) {
        projectClosureService.passProjectClosure(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回项目闭环（2审批中 → 4已驳回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:audit')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) {
        projectClosureService.rejectProjectClosure(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档项目闭环（3已通过 → 5已归档）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:audit')")
    public CommonResult<Boolean> archive(@RequestParam("id") Long id) {
        projectClosureService.archiveProjectClosure(id);
        return success(true);
    }

}
