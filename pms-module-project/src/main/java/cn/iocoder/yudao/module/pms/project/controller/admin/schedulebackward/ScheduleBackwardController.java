package cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardItemRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardItemDO;
import cn.iocoder.yudao.module.pms.project.service.schedulebackward.ScheduleBackwardService;
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
 * 管理后台 - PMS 工期倒排 Controller（FR-PROJ-018）。
 * <p>
 * 路径前缀 {@code /pms/schedule-backward}，对应菜单权限 {@code pms:schedule-backward:*}。
 * 提供倒排记录 CRUD、计算（逆序推算阶段日期并校验）、应用（更新到项目阶段）能力。
 */
@Tag(name = "管理后台 - PMS 工期倒排")
@RestController
@RequestMapping("/pms/schedule-backward")
@Validated
public class ScheduleBackwardController {

    @Resource
    private ScheduleBackwardService scheduleBackwardService;

    @PostMapping("/create")
    @Operation(summary = "创建工期倒排记录")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:create')")
    public CommonResult<Long> createScheduleBackward(@Valid @RequestBody ScheduleBackwardSaveReqVO createReqVO) {
        return success(scheduleBackwardService.createScheduleBackward(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工期倒排记录")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:update')")
    public CommonResult<Boolean> updateScheduleBackward(@Valid @RequestBody ScheduleBackwardSaveReqVO updateReqVO) {
        scheduleBackwardService.updateScheduleBackward(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工期倒排记录")
    @Parameter(name = "id", description = "倒排记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:delete')")
    public CommonResult<Boolean> deleteScheduleBackward(@RequestParam("id") Long id) {
        scheduleBackwardService.deleteScheduleBackward(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工期倒排记录详情（含明细）")
    @Parameter(name = "id", description = "倒排记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:query')")
    public CommonResult<ScheduleBackwardRespVO> getScheduleBackward(@RequestParam("id") Long id) {
        ScheduleBackwardDO backward = scheduleBackwardService.getScheduleBackward(id);
        ScheduleBackwardRespVO respVO = BeanUtils.toBean(backward, ScheduleBackwardRespVO.class);
        if (respVO != null) {
            List<ScheduleBackwardItemDO> items = scheduleBackwardService.getScheduleBackwardItems(id);
            respVO.setItems(BeanUtils.toBean(items, ScheduleBackwardItemRespVO.class));
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得工期倒排记录分页")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:query')")
    public CommonResult<PageResult<ScheduleBackwardRespVO>> getScheduleBackwardPage(
            @Validated ScheduleBackwardPageReqVO pageReqVO) {
        PageResult<ScheduleBackwardDO> pageResult = scheduleBackwardService.getScheduleBackwardPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ScheduleBackwardRespVO.class));
    }

    @GetMapping("/items")
    @Operation(summary = "查询倒排阶段明细列表")
    @Parameter(name = "backwardId", description = "倒排记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:query')")
    public CommonResult<List<ScheduleBackwardItemRespVO>> getScheduleBackwardItems(
            @RequestParam("backwardId") Long backwardId) {
        List<ScheduleBackwardItemDO> list = scheduleBackwardService.getScheduleBackwardItems(backwardId);
        return success(BeanUtils.toBean(list, ScheduleBackwardItemRespVO.class));
    }

    @PostMapping("/calculate")
    @Operation(summary = "计算工期倒排（逆序推算阶段日期并校验）")
    @Parameter(name = "id", description = "倒排记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:calculate')")
    public CommonResult<List<ScheduleBackwardItemRespVO>> calculateScheduleBackward(@RequestParam("id") Long id) {
        List<ScheduleBackwardItemDO> items = scheduleBackwardService.calculateScheduleBackward(id);
        return success(BeanUtils.toBean(items, ScheduleBackwardItemRespVO.class));
    }

    @PostMapping("/apply")
    @Operation(summary = "应用工期倒排到项目阶段")
    @Parameter(name = "id", description = "倒排记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:schedule-backward:apply')")
    public CommonResult<Boolean> applyScheduleBackward(@RequestParam("id") Long id) {
        scheduleBackwardService.applyScheduleBackward(id);
        return success(true);
    }

}
