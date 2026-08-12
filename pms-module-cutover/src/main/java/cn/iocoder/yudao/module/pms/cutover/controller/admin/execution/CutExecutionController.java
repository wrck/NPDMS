package cn.iocoder.yudao.module.pms.cutover.controller.admin.execution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.execution.CutExecutionDO;
import cn.iocoder.yudao.module.pms.cutover.service.execution.CutExecutionService;
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
 * 管理后台 - PMS 割接执行 Controller（FR-CUT-011 / FR-CUT-012）。
 * <p>
 * 路径前缀 {@code /pms/cut-execution}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:cut-execution:*}。
 */
@Tag(name = "管理后台 - PMS 割接执行")
@RestController
@RequestMapping("/pms/cut-execution")
@Validated
public class CutExecutionController {

    @Resource
    private CutExecutionService cutExecutionService;

    @PostMapping("/create")
    @Operation(summary = "创建割接执行记录")
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:create')")
    public CommonResult<Long> createCutExecution(@Valid @RequestBody CutExecutionSaveReqVO createReqVO) {
        return success(cutExecutionService.createCutExecution(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新割接执行记录")
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:update')")
    public CommonResult<Boolean> updateCutExecution(@Valid @RequestBody CutExecutionSaveReqVO updateReqVO) {
        cutExecutionService.updateCutExecution(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除割接执行记录")
    @Parameter(name = "id", description = "执行编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:delete')")
    public CommonResult<Boolean> deleteCutExecution(@RequestParam("id") Long id) {
        cutExecutionService.deleteCutExecution(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询割接执行详情")
    @Parameter(name = "id", description = "执行编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:query')")
    public CommonResult<CutExecutionRespVO> getCutExecution(@RequestParam("id") Long id) {
        CutExecutionDO entity = cutExecutionService.getCutExecution(id);
        return success(BeanUtils.toBean(entity, CutExecutionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询割接执行记录")
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:query')")
    public CommonResult<PageResult<CutExecutionRespVO>> getCutExecutionPage(@Validated CutExecutionPageReqVO pageReqVO) {
        PageResult<CutExecutionDO> pageResult = cutExecutionService.getCutExecutionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CutExecutionRespVO.class));
    }

    @PutMapping("/start")
    @Operation(summary = "开始执行（0待执行 → 1执行中）")
    @Parameter(name = "id", description = "执行编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:update')")
    public CommonResult<Boolean> start(@RequestParam("id") Long id) {
        cutExecutionService.start(id);
        return success(true);
    }

    @PutMapping("/pass")
    @Operation(summary = "通过（1执行中 → 2已通过）")
    @Parameter(name = "id", description = "执行编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:update')")
    public CommonResult<Boolean> pass(@RequestParam("id") Long id) {
        cutExecutionService.pass(id);
        return success(true);
    }

    @PutMapping("/fail")
    @Operation(summary = "失败（1执行中 → 3失败）")
    @Parameter(name = "id", description = "执行编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:update')")
    public CommonResult<Boolean> fail(@RequestParam("id") Long id) {
        cutExecutionService.fail(id);
        return success(true);
    }

    @PutMapping("/rollback")
    @Operation(summary = "回退（1执行中 → 4已回退）")
    @Parameter(name = "id", description = "执行编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-execution:update')")
    public CommonResult<Boolean> rollback(@RequestParam("id") Long id) {
        cutExecutionService.rollback(id);
        return success(true);
    }
}
