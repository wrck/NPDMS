package cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution.SrvExecutionDO;
import cn.iocoder.yudao.module.pms.service.service.srvexecution.SrvExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 巡检执行记录")
@RestController
@RequestMapping("/pms/srv-execution")
@Validated
public class SrvExecutionController {

    @Resource
    private SrvExecutionService srvExecutionService;

    @PostMapping("/create")
    @Operation(summary = "创建巡检执行记录")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:create')")
    public CommonResult<Long> createSrvExecution(@Valid @RequestBody SrvExecutionSaveReqVO createReqVO) {
        return success(srvExecutionService.createSrvExecution(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新巡检执行记录")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> updateSrvExecution(@Valid @RequestBody SrvExecutionSaveReqVO updateReqVO) {
        srvExecutionService.updateSrvExecution(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除巡检执行记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:delete')")
    public CommonResult<Boolean> deleteSrvExecution(@RequestParam("id") Long id) {
        srvExecutionService.deleteSrvExecution(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得巡检执行记录分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:query')")
    public CommonResult<PageResult<SrvExecutionRespVO>> getSrvExecutionPage(@Validated SrvExecutionPageReqVO pageReqVO) {
        PageResult<SrvExecutionDO> pageResult = srvExecutionService.getSrvExecutionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvExecutionRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得巡检执行记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:query')")
    public CommonResult<SrvExecutionRespVO> getSrvExecution(@RequestParam("id") Long id) {
        SrvExecutionDO execution = srvExecutionService.getSrvExecution(id);
        return success(BeanUtils.toBean(execution, SrvExecutionRespVO.class));
    }

    @PutMapping("/start-execution")
    @Operation(summary = "开始执行")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> startExecution(@RequestParam("id") Long id) {
        srvExecutionService.startExecution(id);
        return success(true);
    }

    @PutMapping("/complete-execution")
    @Operation(summary = "完成执行")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> completeExecution(@RequestParam("id") Long id) {
        srvExecutionService.completeExecution(id);
        return success(true);
    }

    @PutMapping("/mark-abnormal")
    @Operation(summary = "标记异常")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> markAbnormal(@RequestParam("id") Long id) {
        srvExecutionService.markAbnormal(id);
        return success(true);
    }

}
