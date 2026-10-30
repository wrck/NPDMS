package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchivePageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveStatusChangeReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveVersionRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceConfigurationLogPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceConfigurationLogRespVO;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceConfigurationLogQueryService;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 设备档案管理 Controller（FR-RES-001/FR-RES-002，ast_device 承载）。
 * <p>
 * 路径前缀 {@code /pms/asset/devices}（与设备查询/归属 Controller 同前缀），
 * 权限 {@code pms:device:create/update/delete/status-change} 与 {@code pms:device-version:query}
 * （自 /pms/equipment 旧链承接）。
 */
@Tag(name = "管理后台 - 设备档案管理")
@RestController
@RequestMapping("/pms/asset/devices")
@RequiredArgsConstructor
public class DeviceArchiveController {

    private final DeviceArchiveService deviceArchiveService;
    private final DeviceConfigurationLogQueryService configurationLogQueryService;

    @PostMapping
    @Operation(summary = "创建设备档案")
    @PreAuthorize("@ss.hasPermission('pms:device:create')")
    public CommonResult<Long> createDevice(@Valid @RequestBody DeviceArchiveSaveReqVO createReqVO) {
        return success(deviceArchiveService.createDevice(createReqVO));
    }

    @GetMapping("/archive-page")
    @Operation(summary = "分页查询设备档案")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceArchiveRespVO>> getDeviceArchivePage(
            @Valid DeviceArchivePageReqVO pageReqVO) {
        return success(BeanUtils.toBean(deviceArchiveService.getDeviceArchivePage(pageReqVO),
                DeviceArchiveRespVO.class));
    }

    @GetMapping("/{id}/archive-record")
    @Operation(summary = "查询单个设备档案记录")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<DeviceArchiveRespVO> getDeviceArchiveRecord(@PathVariable("id") Long id) {
        return success(BeanUtils.toBean(deviceArchiveService.getDeviceArchiveRecord(id),
                DeviceArchiveRespVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新设备档案（已报废不允许修改）")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:device:update')")
    public CommonResult<Boolean> updateDevice(@PathVariable("id") Long id,
                                               @Valid @RequestBody DeviceArchiveSaveReqVO updateReqVO) {
        updateReqVO.setId(id);
        deviceArchiveService.updateDevice(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除设备档案")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:device:delete')")
    public CommonResult<Boolean> deleteDevice(@PathVariable("id") Long id) {
        deviceArchiveService.deleteDevice(id);
        return success(true);
    }

    @PostMapping("/{id}/actions/status-change")
    @Operation(summary = "设备状态变更（状态机校验 + 版本历史追加）")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:device:status-change')")
    public CommonResult<Boolean> changeDeviceStatus(@PathVariable("id") Long id,
                                                     @Valid @RequestBody DeviceArchiveStatusChangeReqVO reqVO) {
        deviceArchiveService.changeDeviceStatus(id, reqVO);
        return success(true);
    }

    @GetMapping("/{id}/archive-versions")
    @Operation(summary = "查询设备版本历史列表（追加只读）")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:device-version:query')")
    public CommonResult<List<DeviceArchiveVersionRespVO>> getDeviceVersionList(@PathVariable("id") Long id) {
        return success(BeanUtils.toBean(deviceArchiveService.getDeviceVersionList(id),
                DeviceArchiveVersionRespVO.class));
    }

    @GetMapping("/configuration-logs/page")
    @Operation(summary = "分页查询设备配置日志")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceConfigurationLogRespVO>> getConfigurationLogPage(
            @Valid DeviceConfigurationLogPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(configurationLogQueryService.getPage(pageReqVO),
                DeviceConfigurationLogRespVO.class));
    }
}
