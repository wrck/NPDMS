package cn.iocoder.yudao.module.pms.project.controller.admin.planchange;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePhaseSnapshotRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangePhaseSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangeRequestDO;
import cn.iocoder.yudao.module.pms.project.service.planchange.PlanChangeService;
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

/**
 * PMS 项目计划变更审批 Controller（FR-PROJ-020 / T-V2-PROJ-003）
 * <p>
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已通过 → 4已驳回 → 5已撤回 → 6已终止
 */
@Tag(name = "管理后台 - PMS 计划变更审批")
@RestController
@RequestMapping("/pms/plan-change")
@Validated
public class PlanChangeController {

    @Resource
    private PlanChangeService planChangeService;

    @PostMapping("/create")
    @Operation(summary = "创建计划变更")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:create')")
    public CommonResult<Long> create(@Valid @RequestBody PlanChangeSaveReqVO createReqVO) {
        return success(planChangeService.createPlanChange(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新计划变更（仅草稿态）")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody PlanChangeSaveReqVO updateReqVO) {
        planChangeService.updatePlanChange(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除计划变更（仅草稿/已驳回态）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        planChangeService.deletePlanChange(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得计划变更分页")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:query')")
    public CommonResult<PageResult<PlanChangeRespVO>> getPage(@Validated PlanChangePageReqVO pageReqVO) {
        PageResult<PlanChangeRequestDO> pageResult = planChangeService.getPlanChangePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, PlanChangeRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得计划变更详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:query')")
    public CommonResult<PlanChangeRespVO> get(@RequestParam("id") Long id) {
        PlanChangeRequestDO entity = planChangeService.getPlanChange(id);
        return success(BeanUtils.toBean(entity, PlanChangeRespVO.class));
    }

    @GetMapping("/snapshots")
    @Operation(summary = "获得计划变更阶段快照列表")
    @Parameter(name = "changeRequestId", description = "变更申请编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:query')")
    public CommonResult<List<PlanChangePhaseSnapshotRespVO>> getSnapshots(
            @RequestParam("changeRequestId") Long changeRequestId) {
        List<PlanChangePhaseSnapshotDO> list = planChangeService.getPhaseSnapshots(changeRequestId);
        return success(BeanUtils.toBean(list, PlanChangePhaseSnapshotRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交计划变更（0草稿/4已驳回 → 1已提交）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        planChangeService.submitPlanChange(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批计划变更（1已提交/2审批中 → 3已通过/4已驳回/0草稿/2审批中）")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody PlanChangeApproveReqVO reqVO) {
        planChangeService.approvePlanChange(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回计划变更（1已提交/2审批中 → 5已撤回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:submit')")
    public CommonResult<Boolean> withdraw(@RequestParam("id") Long id) {
        planChangeService.withdrawPlanChange(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止计划变更（任意非已通过状态 → 6已终止）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:audit')")
    public CommonResult<Boolean> terminate(@RequestParam("id") Long id) {
        planChangeService.terminatePlanChange(id);
        return success(true);
    }

    @PutMapping("/apply")
    @Operation(summary = "应用变更到项目阶段（3已通过 → 写入阶段新计划时间）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:audit')")
    public CommonResult<Boolean> apply(@RequestParam("id") Long id) {
        planChangeService.applyPlanChange(id);
        return success(true);
    }

}
