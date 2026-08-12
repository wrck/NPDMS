package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionGenerateDraftReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionService;
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
 * 管理后台 - 实施方案 Controller（FR-ENG-011 / FR-ENG-013）。
 * <p>
 * 路径前缀 {@code /pms/eng-solution}。
 */
@Tag(name = "管理后台 - 实施方案")
@RestController
@RequestMapping("/pms/eng-solution")
@Validated
public class SolutionController {

    @Resource
    private SolutionService solutionService;

    @PostMapping("/create")
    @Operation(summary = "创建实施方案")
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:create')")
    public CommonResult<Long> createSolution(@Valid @RequestBody SolutionSaveReqVO createReqVO) {
        return success(solutionService.createSolution(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新实施方案")
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> updateSolution(@Valid @RequestBody SolutionSaveReqVO updateReqVO) {
        solutionService.updateSolution(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除实施方案")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:delete')")
    public CommonResult<Boolean> deleteSolution(@RequestParam("id") Long id) {
        solutionService.deleteSolution(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询实施方案详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:query')")
    public CommonResult<SolutionRespVO> getSolution(@RequestParam("id") Long id) {
        SolutionDO solution = solutionService.getSolution(id);
        return success(BeanUtils.toBean(solution, SolutionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询实施方案")
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:query')")
    public CommonResult<PageResult<SolutionRespVO>> getSolutionPage(@Validated SolutionPageReqVO pageReqVO) {
        PageResult<SolutionDO> pageResult = solutionService.getSolutionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SolutionRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交方案")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> submitSolution(@RequestParam("id") Long id) {
        solutionService.submitSolution(id);
        return success(true);
    }

    @PutMapping("/start-review")
    @Operation(summary = "开始评审")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> startReview(@RequestParam("id") Long id) {
        solutionService.startReview(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批通过")
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> approveSolution(@Valid @RequestBody SolutionApproveReqVO reqVO) {
        solutionService.approveSolution(reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "审批驳回")
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> rejectSolution(@Valid @RequestBody SolutionApproveReqVO reqVO) {
        solutionService.rejectSolution(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回方案")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> withdrawSolution(@RequestParam("id") Long id) {
        solutionService.withdrawSolution(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止方案")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:update')")
    public CommonResult<Boolean> terminateSolution(@RequestParam("id") Long id) {
        solutionService.terminateSolution(id);
        return success(true);
    }

    @PostMapping("/generate-draft")
    @Operation(summary = "生成方案草稿")
    @PreAuthorize("@ss.hasPermission('pms:eng-solution:create')")
    public CommonResult<Long> generateDraft(@Valid @RequestBody SolutionGenerateDraftReqVO reqVO) {
        return success(solutionService.generateDraft(reqVO));
    }
}
