package cn.iocoder.yudao.module.pms.cutover.controller.admin.observation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.observation.CutObservationDO;
import cn.iocoder.yudao.module.pms.cutover.service.observation.CutObservationService;
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
 * 管理后台 - PMS 稳定观察 Controller（FR-CUT-013 / FR-CUT-014）。
 * <p>
 * 路径前缀 {@code /pms/cut-observation}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:cut-observation:*}。
 */
@Tag(name = "管理后台 - PMS 稳定观察")
@RestController
@RequestMapping("/pms/cut-observation")
@Validated
public class CutObservationController {

    @Resource
    private CutObservationService cutObservationService;

    @PostMapping("/create")
    @Operation(summary = "创建稳定观察记录")
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:create')")
    public CommonResult<Long> createCutObservation(@Valid @RequestBody CutObservationSaveReqVO createReqVO) {
        return success(cutObservationService.createCutObservation(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新稳定观察记录")
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:update')")
    public CommonResult<Boolean> updateCutObservation(@Valid @RequestBody CutObservationSaveReqVO updateReqVO) {
        cutObservationService.updateCutObservation(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除稳定观察记录")
    @Parameter(name = "id", description = "观察编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:delete')")
    public CommonResult<Boolean> deleteCutObservation(@RequestParam("id") Long id) {
        cutObservationService.deleteCutObservation(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询稳定观察详情")
    @Parameter(name = "id", description = "观察编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:query')")
    public CommonResult<CutObservationRespVO> getCutObservation(@RequestParam("id") Long id) {
        CutObservationDO entity = cutObservationService.getCutObservation(id);
        return success(BeanUtils.toBean(entity, CutObservationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询稳定观察记录")
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:query')")
    public CommonResult<PageResult<CutObservationRespVO>> getCutObservationPage(@Validated CutObservationPageReqVO pageReqVO) {
        PageResult<CutObservationDO> pageResult = cutObservationService.getCutObservationPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CutObservationRespVO.class));
    }

    @PutMapping("/pass")
    @Operation(summary = "观察通过（0观察中 → 1已通过）")
    @Parameter(name = "id", description = "观察编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:update')")
    public CommonResult<Boolean> pass(@RequestParam("id") Long id) {
        cutObservationService.pass(id);
        return success(true);
    }

    @PutMapping("/mark-abnormal")
    @Operation(summary = "标记异常（0观察中 → 2异常）")
    @Parameter(name = "id", description = "观察编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:update')")
    public CommonResult<Boolean> markAbnormal(@RequestParam("id") Long id) {
        cutObservationService.markAbnormal(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档（1已通过 → 3已归档），归档前校验遗留项已闭环")
    @Parameter(name = "id", description = "观察编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-observation:update')")
    public CommonResult<Boolean> archive(@RequestParam("id") Long id) {
        cutObservationService.archive(id);
        return success(true);
    }
}
