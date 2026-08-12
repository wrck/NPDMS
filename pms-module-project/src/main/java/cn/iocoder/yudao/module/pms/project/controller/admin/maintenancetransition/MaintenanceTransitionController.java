package cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.maintenancetransition.MaintenanceTransitionDO;
import cn.iocoder.yudao.module.pms.project.service.maintenancetransition.MaintenanceTransitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 转维保")
@RestController
@RequestMapping("/pms/acc-maintenance-transition")
@Validated
public class MaintenanceTransitionController {

    @Resource
    private MaintenanceTransitionService maintenanceTransitionService;

    @PostMapping("/create")
    @Operation(summary = "创建转维保")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:create')")
    public CommonResult<Long> create(@Valid @RequestBody MaintenanceTransitionSaveReqVO createReqVO) {
        return success(maintenanceTransitionService.createMaintenanceTransition(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新转维保")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody MaintenanceTransitionSaveReqVO updateReqVO) {
        maintenanceTransitionService.updateMaintenanceTransition(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除转维保")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        maintenanceTransitionService.deleteMaintenanceTransition(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得转维保分页")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:query')")
    public CommonResult<PageResult<MaintenanceTransitionRespVO>> getPage(@Validated MaintenanceTransitionPageReqVO pageReqVO) {
        PageResult<MaintenanceTransitionDO> pageResult = maintenanceTransitionService.getMaintenanceTransitionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MaintenanceTransitionRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得转维保")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:query')")
    public CommonResult<MaintenanceTransitionRespVO> get(@RequestParam("id") Long id) {
        MaintenanceTransitionDO entity = maintenanceTransitionService.getMaintenanceTransition(id);
        return success(BeanUtils.toBean(entity, MaintenanceTransitionRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交转维保（0草稿 → 1待生效）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        maintenanceTransitionService.submitMaintenanceTransition(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "生效转维保（1待生效 → 2生效中，自动生成维护期）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:audit')")
    public CommonResult<Boolean> activate(@RequestParam("id") Long id) {
        maintenanceTransitionService.activate(id);
        return success(true);
    }

    @PutMapping("/expire")
    @Operation(summary = "过期转维保（2生效中 → 3已过期）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:audit')")
    public CommonResult<Boolean> expire(@RequestParam("id") Long id) {
        maintenanceTransitionService.expire(id);
        return success(true);
    }

    @PutMapping("/renew")
    @Operation(summary = "续保转维保（3已过期 → 4已续保）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-maintenance-transition:audit')")
    public CommonResult<Boolean> renew(@RequestParam("id") Long id) {
        maintenanceTransitionService.renew(id);
        return success(true);
    }

}
