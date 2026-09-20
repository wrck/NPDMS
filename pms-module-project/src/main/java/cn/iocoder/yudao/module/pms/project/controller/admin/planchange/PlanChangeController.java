package cn.iocoder.yudao.module.pms.project.controller.admin.planchange;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePhaseSnapshotRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangePhaseSnapshotRetiredDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangeRequestRetiredDO;
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
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Tag(name = "管理后台 - PMS 计划变更审批")
@RestController
@RequestMapping("/pms/plan-change")
@Validated
@Deprecated
public class PlanChangeController {

    @Resource
    private PlanChangeService planChangeService;

    @PostMapping("/create")
    @Operation(summary = "创建计划变更")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:create')")
    public CommonResult<Long> create(@Valid @RequestBody PlanChangeSaveReqVO createReqVO) {
        return success(planChangeService.createPlanChangeRetired(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新计划变更（仅草稿态）")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody PlanChangeSaveReqVO updateReqVO) {
        planChangeService.updatePlanChangeRetired(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除计划变更（仅草稿/已驳回态）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        planChangeService.deletePlanChangeRetired(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得计划变更分页")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:query')")
    public CommonResult<PageResult<PlanChangeRespVO>> getPage(@Validated PlanChangePageReqVO pageReqVO) {
        PageResult<PlanChangeRequestRetiredDO> pageResult = planChangeService.getPlanChangePageRetired(pageReqVO);
        return success(BeanUtils.toBean(pageResult, PlanChangeRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得计划变更详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:query')")
    public CommonResult<PlanChangeRespVO> get(@RequestParam("id") Long id) {
        PlanChangeRequestRetiredDO entity = planChangeService.getPlanChangeRetired(id);
        return success(BeanUtils.toBean(entity, PlanChangeRespVO.class));
    }

    @GetMapping("/snapshots")
    @Operation(summary = "获得计划变更阶段快照列表")
    @Parameter(name = "changeRequestId", description = "变更申请编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:query')")
    public CommonResult<List<PlanChangePhaseSnapshotRespVO>> getSnapshots(
            @RequestParam("changeRequestId") Long changeRequestId) {
        List<PlanChangePhaseSnapshotRetiredDO> list = planChangeService.getPhaseSnapshotsRetired(changeRequestId);
        return success(BeanUtils.toBean(list, PlanChangePhaseSnapshotRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交计划变更（0草稿/4已驳回 → 1已提交）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        planChangeService.submitPlanChangeRetired(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批计划变更（1已提交/2审批中 → 3已通过/4已驳回/0草稿/2审批中）")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody PlanChangeApproveReqVO reqVO) {
        planChangeService.approvePlanChangeRetired(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回计划变更（1已提交/2审批中 → 5已撤回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:submit')")
    public CommonResult<Boolean> withdraw(@RequestParam("id") Long id) {
        planChangeService.withdrawPlanChangeRetired(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止计划变更（任意非已通过状态 → 6已终止）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:audit')")
    public CommonResult<Boolean> terminate(@RequestParam("id") Long id) {
        planChangeService.terminatePlanChangeRetired(id);
        return success(true);
    }

    @PutMapping("/apply")
    @Operation(summary = "应用变更到项目阶段（3已通过 → 写入阶段新计划时间）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:plan-change:audit')")
    public CommonResult<Boolean> apply(@RequestParam("id") Long id) {
        planChangeService.applyPlanChangeRetired(id);
        return success(true);
    }

}
