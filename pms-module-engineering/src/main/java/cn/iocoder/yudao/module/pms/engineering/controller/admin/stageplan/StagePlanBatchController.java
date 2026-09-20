package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueSummaryVO;
import cn.iocoder.yudao.module.pms.engineering.service.stageplan.StagePlanBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 阶段施工计划 Controller（PLN-01/04，Demo 3.1）。
 * <p>
 * 路径前缀 {@code /pms/imp-stage-plan}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:imp-stage-plan:*}。审批生效由 BPM 回调写回阶段计划日期。
 */
@Tag(name = "管理后台 - PMS 阶段施工计划")
@RestController
@RequestMapping("/pms/imp-stage-plan")
@Validated
public class StagePlanBatchController {

    @Resource
    private StagePlanBatchService stagePlanBatchService;

    @PostMapping("/create")
    @Operation(summary = "创建阶段施工计划草稿（读取项目阶段事实生成明细）")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:create')")
    public CommonResult<StagePlanBatchRespVO> createDraft(@RequestParam("projectId") @Positive Long projectId) {
        return success(stagePlanBatchService.createDraft(projectId));
    }

    @PutMapping("/{id}/auto-estimate")
    @Operation(summary = "自动推算（工期基线窗口内按建议工期占比分摊；无基线带入建议起止）")
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:update')")
    public CommonResult<StagePlanBatchRespVO> autoEstimate(@PathVariable("id") @Positive Long id) {
        return success(stagePlanBatchService.autoEstimate(id));
    }

    @PutMapping("/update-items")
    @Operation(summary = "人工调整阶段计划时间（仅草稿/已驳回，校验重叠与基线窗口）")
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:update')")
    public CommonResult<StagePlanBatchRespVO> updateItems(
            @Valid @RequestBody StagePlanItemUpdateReqVO updateReqVO) {
        return success(stagePlanBatchService.updateItems(updateReqVO));
    }

    @PutMapping("/{id}/submit")
    @Operation(summary = "提交审批（发起 BPM 流程，审批通过后生效并回写阶段计划日期）")
    @Parameter(name = "approverUserId", description = "审批人用户编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:submit')")
    public CommonResult<StagePlanBatchRespVO> submit(@PathVariable("id") @Positive Long id,
                                                     @RequestParam("approverUserId") @Positive Long approverUserId) {
        return success(stagePlanBatchService.submit(id, approverUserId));
    }

    @GetMapping("/get")
    @Operation(summary = "查询阶段施工计划批次详情（含阶段明细与工期基线窗口）")
    @Parameter(name = "id", description = "批次编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:query')")
    public CommonResult<StagePlanBatchRespVO> getBatch(@RequestParam("id") @Positive Long id) {
        return success(stagePlanBatchService.getBatch(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询阶段施工计划批次")
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:query')")
    public CommonResult<PageResult<StagePlanBatchRespVO>> getBatchPage(
            @Validated StagePlanBatchPageReqVO pageReqVO) {
        return success(stagePlanBatchService.getBatchPage(pageReqVO));
    }

    @GetMapping("/overdue-stages")
    @Operation(summary = "超期阶段清单（PLN-03，仅生效计划版本，计算结果）")
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:query')")
    public CommonResult<List<StagePlanOverdueRowVO>> getOverdueStages(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        return success(stagePlanBatchService.getOverdueStages(projectId));
    }

    @GetMapping("/overdue-summary")
    @Operation(summary = "超期统计汇总（PLN-03，与超期阶段清单同口径）")
    @PreAuthorize("@ss.hasPermission('pms:imp-stage-plan:query')")
    public CommonResult<StagePlanOverdueSummaryVO> getOverdueSummary(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        return success(stagePlanBatchService.getOverdueSummary(projectId));
    }
}
