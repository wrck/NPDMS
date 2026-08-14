package cn.iocoder.yudao.module.pms.cutover.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutTaskService;
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
 * 管理后台 - PMS 割接任务 Controller（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 * <p>
 * 路径前缀 {@code /pms/cut-task}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:cut-task:*}。
 */
@Tag(name = "管理后台 - PMS 割接任务")
@RestController
@RequestMapping("/pms/cut-task")
@Validated
public class CutTaskController {

    @Resource
    private CutTaskService cutTaskService;

    @PostMapping("/create")
    @Operation(summary = "创建割接任务")
    @PreAuthorize("@ss.hasPermission('pms:cut-task:create')")
    public CommonResult<Long> createCutTask(@Valid @RequestBody CutTaskSaveReqVO createReqVO) {
        return success(cutTaskService.createCutTask(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新割接任务")
    @PreAuthorize("@ss.hasPermission('pms:cut-task:update')")
    public CommonResult<Boolean> updateCutTask(@Valid @RequestBody CutTaskSaveReqVO updateReqVO) {
        cutTaskService.updateCutTask(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除割接任务")
    @Parameter(name = "id", description = "割接任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-task:delete')")
    public CommonResult<Boolean> deleteCutTask(@RequestParam("id") Long id) {
        cutTaskService.deleteCutTask(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询割接任务详情")
    @Parameter(name = "id", description = "割接任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-task:query')")
    public CommonResult<CutTaskRespVO> getCutTask(@RequestParam("id") Long id) {
        CutTaskDO entity = cutTaskService.getCutTask(id);
        return success(BeanUtils.toBean(entity, CutTaskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询割接任务")
    @PreAuthorize("@ss.hasPermission('pms:cut-task:query')")
    public CommonResult<PageResult<CutTaskRespVO>> getCutTaskPage(@Validated CutTaskPageReqVO pageReqVO) {
        PageResult<CutTaskDO> pageResult = cutTaskService.getCutTaskPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CutTaskRespVO.class));
    }

    @PutMapping("/submit-for-review")
    @Operation(summary = "提交评审（0草稿 → 2待评审）")
    @Parameter(name = "id", description = "割接任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-task:update')")
    public CommonResult<Boolean> submitForReview(@RequestParam("id") Long id) {
        cutTaskService.submitForReview(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "评审通过（2待评审 → 3闭环中）")
    @PreAuthorize("@ss.hasPermission('pms:cut-task:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody CutTaskApproveReqVO reqVO) {
        cutTaskService.approve(reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "评审驳回（2待评审 → 1准备中）")
    @PreAuthorize("@ss.hasPermission('pms:cut-task:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody CutTaskApproveReqVO reqVO) {
        cutTaskService.reject(reqVO);
        return success(true);
    }

}
