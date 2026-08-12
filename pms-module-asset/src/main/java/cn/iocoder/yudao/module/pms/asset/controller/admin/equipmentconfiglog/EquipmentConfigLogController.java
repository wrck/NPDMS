package cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo.EquipmentConfigLogPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo.EquipmentConfigLogRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.service.equipmentconfiglog.EquipmentConfigLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 设备配置日志 Controller（FR-RES-003）。
 * <p>
 * 路径前缀 {@code /pms/equipment/config-log}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:equipment-config:query}。
 * 配置日志为只读档案，仅提供分页查询。
 */
@Tag(name = "管理后台 - PMS 设备配置日志")
@RestController
@RequestMapping("/pms/equipment/config-log")
@Validated
public class EquipmentConfigLogController {

    @Resource
    private EquipmentConfigLogService equipmentConfigLogService;

    @GetMapping("/page")
    @Operation(summary = "分页查询设备配置日志")
    @PreAuthorize("@ss.hasPermission('pms:equipment-config:query')")
    public CommonResult<PageResult<EquipmentConfigLogRespVO>> getEquipmentConfigLogPage(
            @Valid EquipmentConfigLogPageReqVO pageReqVO) {
        PageResult<EquipmentConfigLogDO> pageResult = equipmentConfigLogService.getEquipmentConfigLogPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, EquipmentConfigLogRespVO.class));
    }

}
