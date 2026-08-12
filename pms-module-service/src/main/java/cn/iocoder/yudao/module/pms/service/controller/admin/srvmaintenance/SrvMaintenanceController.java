package cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceOverrideReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenancePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvmaintenance.SrvMaintenanceDO;
import cn.iocoder.yudao.module.pms.service.service.srvmaintenance.SrvMaintenanceService;
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

@Tag(name = "管理后台 - 维保状态")
@RestController
@RequestMapping("/pms/srv-maintenance")
@Validated
public class SrvMaintenanceController {

    @Resource
    private SrvMaintenanceService srvMaintenanceService;

    @PostMapping("/create")
    @Operation(summary = "创建维保记录")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:create')")
    public CommonResult<Long> createSrvMaintenance(@Valid @RequestBody SrvMaintenanceSaveReqVO createReqVO) {
        return success(srvMaintenanceService.createSrvMaintenance(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新维保记录")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:update')")
    public CommonResult<Boolean> updateSrvMaintenance(@Valid @RequestBody SrvMaintenanceSaveReqVO updateReqVO) {
        srvMaintenanceService.updateSrvMaintenance(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除维保记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:delete')")
    public CommonResult<Boolean> deleteSrvMaintenance(@RequestParam("id") Long id) {
        srvMaintenanceService.deleteSrvMaintenance(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得维保记录分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:query')")
    public CommonResult<PageResult<SrvMaintenanceRespVO>> getSrvMaintenancePage(@Validated SrvMaintenancePageReqVO pageReqVO) {
        PageResult<SrvMaintenanceDO> pageResult = srvMaintenanceService.getSrvMaintenancePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvMaintenanceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得维保记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:query')")
    public CommonResult<SrvMaintenanceRespVO> getSrvMaintenance(@RequestParam("id") Long id) {
        SrvMaintenanceDO maintenance = srvMaintenanceService.getSrvMaintenance(id);
        return success(BeanUtils.toBean(maintenance, SrvMaintenanceRespVO.class));
    }

    @GetMapping("/list-by-equipment")
    @Operation(summary = "根据设备编号获得维保记录列表")
    @Parameter(name = "equipmentId", description = "设备编号", required = true, example = "200")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:query')")
    public CommonResult<List<SrvMaintenanceRespVO>> getSrvMaintenanceListByEquipment(@RequestParam("equipmentId") Long equipmentId) {
        List<SrvMaintenanceDO> list = srvMaintenanceService.getSrvMaintenanceListByEquipment(equipmentId);
        return success(BeanUtils.toBean(list, SrvMaintenanceRespVO.class));
    }

    @PutMapping("/calculate")
    @Operation(summary = "自动计算维保状态")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:update')")
    public CommonResult<Boolean> calculateStatus(@RequestParam("id") Long id) {
        srvMaintenanceService.calculateStatus(id);
        return success(true);
    }

    @PutMapping("/override")
    @Operation(summary = "手工覆盖维保状态")
    @PreAuthorize("@ss.hasPermission('pms:srv-maintenance:update')")
    public CommonResult<Boolean> manualOverride(@Valid @RequestBody SrvMaintenanceOverrideReqVO reqVO) {
        srvMaintenanceService.manualOverride(reqVO);
        return success(true);
    }

}
