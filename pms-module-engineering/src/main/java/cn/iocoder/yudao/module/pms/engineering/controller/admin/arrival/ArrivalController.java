package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.service.arrival.ArrivalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 到货签收 Controller（FR-ENG-021）。
 * <p>
 * 路径前缀 {@code /pms/eng-arrival}。
 */
@Tag(name = "管理后台 - 到货签收")
@RestController
@RequestMapping("/pms/eng-arrival")
@Validated
public class ArrivalController {

    @Resource
    private ArrivalService arrivalService;

    @PostMapping("/create")
    @Operation(summary = "创建到货签收")
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:create')")
    public CommonResult<Long> createArrival(@Valid @RequestBody ArrivalSaveReqVO createReqVO) {
        return success(arrivalService.createArrival(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新到货签收")
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:update')")
    public CommonResult<Boolean> updateArrival(@Valid @RequestBody ArrivalSaveReqVO updateReqVO) {
        arrivalService.updateArrival(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除到货签收")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:delete')")
    public CommonResult<Boolean> deleteArrival(@RequestParam("id") Long id) {
        arrivalService.deleteArrival(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询到货签收详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:query')")
    public CommonResult<ArrivalRespVO> getArrival(@RequestParam("id") Long id) {
        ArrivalDO arrival = arrivalService.getArrival(id);
        return success(BeanUtils.toBean(arrival, ArrivalRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询到货签收")
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:query')")
    public CommonResult<PageResult<ArrivalRespVO>> getArrivalPage(@Validated ArrivalPageReqVO pageReqVO) {
        PageResult<ArrivalDO> pageResult = arrivalService.getArrivalPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ArrivalRespVO.class));
    }

    @PutMapping("/sign")
    @Operation(summary = "签收到货")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:update')")
    public CommonResult<Boolean> signArrival(@RequestParam("id") Long id) {
        arrivalService.signArrival(id);
        return success(true);
    }

    @PutMapping("/mark-abnormal")
    @Operation(summary = "标记到货异常")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-arrival:update')")
    public CommonResult<Boolean> markAbnormal(@RequestParam("id") Long id) {
        arrivalService.markAbnormal(id);
        return success(true);
    }
}
