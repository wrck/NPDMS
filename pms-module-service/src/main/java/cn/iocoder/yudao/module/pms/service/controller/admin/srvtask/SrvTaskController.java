package cn.iocoder.yudao.module.pms.service.controller.admin.srvtask;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskRetiredDO;
import cn.iocoder.yudao.module.pms.service.service.srvtask.SrvTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 巡检任务")
@RestController
@RequestMapping("/pms/srv-task")
@Validated
@Deprecated
public class SrvTaskController {

    @Resource
    private SrvTaskService srvTaskService;

    @PostMapping("/create")
    @Operation(summary = "创建巡检任务")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:create')")
    public CommonResult<Long> createSrvTask(@Valid @RequestBody SrvTaskSaveReqVO createReqVO) {
        return success(srvTaskService.createSrvTaskRetired(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新巡检任务")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> updateSrvTask(@Valid @RequestBody SrvTaskSaveReqVO updateReqVO) {
        srvTaskService.updateSrvTaskRetired(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除巡检任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:delete')")
    public CommonResult<Boolean> deleteSrvTask(@RequestParam("id") Long id) {
        srvTaskService.deleteSrvTaskRetired(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得巡检任务分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:query')")
    public CommonResult<PageResult<SrvTaskRespVO>> getSrvTaskPage(@Validated SrvTaskPageReqVO pageReqVO) {
        PageResult<SrvTaskRetiredDO> pageResult = srvTaskService.getSrvTaskPageRetired(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvTaskRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得巡检任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:query')")
    public CommonResult<SrvTaskRespVO> getSrvTask(@RequestParam("id") Long id) {
        SrvTaskRetiredDO task = srvTaskService.getSrvTaskRetired(id);
        return success(BeanUtils.toBean(task, SrvTaskRespVO.class));
    }

    @PutMapping("/validate-equipment-account")
    @Operation(summary = "校验设备账号有效性")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> validateEquipmentAccount(@RequestParam("id") Long id) {
        srvTaskService.validateEquipmentAccountRetired(id);
        return success(true);
    }

    @PutMapping("/submit")
    @Operation(summary = "提交巡检任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> submitSrvTask(@RequestParam("id") Long id) {
        srvTaskService.submitSrvTaskRetired(id);
        return success(true);
    }

    @PutMapping("/start-execution")
    @Operation(summary = "开始执行")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> startExecution(@RequestParam("id") Long id) {
        srvTaskService.startExecutionRetired(id);
        return success(true);
    }

    @PutMapping("/complete-execution")
    @Operation(summary = "完成执行")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> completeExecution(@RequestParam("id") Long id) {
        srvTaskService.completeExecutionRetired(id);
        return success(true);
    }

    @PutMapping("/confirm-report")
    @Operation(summary = "确认报告")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> confirmReport(@RequestParam("id") Long id) {
        srvTaskService.confirmReportRetired(id);
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消巡检任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> cancelSrvTask(@RequestParam("id") Long id) {
        srvTaskService.cancelSrvTaskRetired(id);
        return success(true);
    }

}
