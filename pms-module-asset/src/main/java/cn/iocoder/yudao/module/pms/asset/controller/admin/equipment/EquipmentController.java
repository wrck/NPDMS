package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentStatusChangeReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentVersionRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentVersionDO;
import cn.iocoder.yudao.module.pms.asset.service.equipment.EquipmentService;
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

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 设备档案 Controller（FR-RES-001 / FR-RES-002）。
 * <p>
 * 路径前缀 {@code /pms/equipment}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:equipment:*} 与 {@code pms:equipment-version:query}。
 */
@Tag(name = "管理后台 - PMS 设备档案")
@RestController
@RequestMapping("/pms/equipment")
@Validated
public class EquipmentController {

    @Resource
    private EquipmentService equipmentService;

    @PostMapping("/create")
    @Operation(summary = "创建设备档案")
    @PreAuthorize("@ss.hasPermission('pms:equipment:create')")
    public CommonResult<Long> createEquipment(@Valid @RequestBody EquipmentSaveReqVO createReqVO) {
        return success(equipmentService.createEquipment(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备档案（已报废不允许修改）")
    @PreAuthorize("@ss.hasPermission('pms:equipment:update')")
    public CommonResult<Boolean> updateEquipment(@Valid @RequestBody EquipmentSaveReqVO updateReqVO) {
        equipmentService.updateEquipment(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备档案")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:equipment:delete')")
    public CommonResult<Boolean> deleteEquipment(@RequestParam("id") Long id) {
        equipmentService.deleteEquipment(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询设备详情")
    @Parameter(name = "id", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:equipment:query')")
    public CommonResult<EquipmentRespVO> getEquipment(@RequestParam("id") Long id) {
        EquipmentDO entity = equipmentService.getEquipment(id);
        return success(BeanUtils.toBean(entity, EquipmentRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询设备")
    @PreAuthorize("@ss.hasPermission('pms:equipment:query')")
    public CommonResult<PageResult<EquipmentRespVO>> getEquipmentPage(@Validated EquipmentPageReqVO pageReqVO) {
        PageResult<EquipmentDO> pageResult = equipmentService.getEquipmentPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, EquipmentRespVO.class));
    }

    @PutMapping("/status-change")
    @Operation(summary = "设备状态变更（状态机校验 + 版本历史追加）")
    @PreAuthorize("@ss.hasPermission('pms:equipment:status-change')")
    public CommonResult<Boolean> changeEquipmentStatus(@Valid @RequestBody EquipmentStatusChangeReqVO reqVO) {
        equipmentService.changeEquipmentStatus(reqVO);
        return success(true);
    }

    @GetMapping("/version/list")
    @Operation(summary = "查询设备版本历史列表（追加只读）")
    @Parameter(name = "equipmentId", description = "设备编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:equipment-version:query')")
    public CommonResult<List<EquipmentVersionRespVO>> getEquipmentVersionList(
            @RequestParam("equipmentId") Long equipmentId) {
        List<EquipmentVersionDO> list = equipmentService.getEquipmentVersionList(equipmentId);
        return success(BeanUtils.toBean(list, EquipmentVersionRespVO.class));
    }

}
