package cn.iocoder.yudao.module.pms.cutover.controller.admin.plan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutPlanDO;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 割接方案 Controller（FR-CUT-008 / FR-CUT-009）。
 * <p>
 * 路径前缀 {@code /pms/cut-plan}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:cut-plan:*}。
 */
@Tag(name = "管理后台 - PMS 割接方案")
@RestController
@RequestMapping("/pms/cut-plan")
@Validated
public class CutPlanController {

    @Resource
    private CutPlanService cutPlanService;

    @PostMapping("/create")
    @Operation(summary = "创建割接方案")
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:create')")
    public CommonResult<Long> createCutPlan(@Valid @RequestBody CutPlanSaveReqVO createReqVO) {
        return success(cutPlanService.createCutPlan(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新割接方案（基线锁定后关键字段不可变更）")
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:update')")
    public CommonResult<Boolean> updateCutPlan(@Valid @RequestBody CutPlanSaveReqVO updateReqVO) {
        cutPlanService.updateCutPlan(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除割接方案")
    @Parameter(name = "id", description = "方案编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:delete')")
    public CommonResult<Boolean> deleteCutPlan(@RequestParam("id") Long id) {
        cutPlanService.deleteCutPlan(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询割接方案详情")
    @Parameter(name = "id", description = "方案编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:query')")
    public CommonResult<CutPlanRespVO> getCutPlan(@RequestParam("id") Long id) {
        CutPlanDO entity = cutPlanService.getCutPlan(id);
        return success(BeanUtils.toBean(entity, CutPlanRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询割接方案")
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:query')")
    public CommonResult<PageResult<CutPlanRespVO>> getCutPlanPage(@Validated CutPlanPageReqVO pageReqVO) {
        PageResult<CutPlanDO> pageResult = cutPlanService.getCutPlanPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CutPlanRespVO.class));
    }

    @PutMapping("/submit-for-review")
    @Operation(summary = "提交评审（0草稿 → 1待评审）")
    @Parameter(name = "id", description = "方案编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:update')")
    public CommonResult<Boolean> submitForReview(@RequestParam("id") Long id) {
        cutPlanService.submitForReview(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "评审通过（1待评审 → 2已通过），冻结基线版本")
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody CutPlanApproveReqVO reqVO) {
        cutPlanService.approve(reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "评审驳回（1待评审 → 3已驳回）")
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody CutPlanApproveReqVO reqVO) {
        cutPlanService.reject(reqVO);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止方案（任意非终态 → 4已终止）")
    @Parameter(name = "id", description = "方案编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-plan:update')")
    public CommonResult<Boolean> terminate(@RequestParam("id") Long id) {
        cutPlanService.terminate(id);
        return success(true);
    }
}
