package cn.iocoder.yudao.module.pms.project.controller.admin.acceptance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptanceRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptanceSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.project.service.acceptance.AcceptanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 验收管理")
@RestController
@RequestMapping("/pms/acc-acceptance")
@Validated
@Deprecated(since = "F-ACC-001", forRemoval = false)
public class AcceptanceController {

    @Resource
    private AcceptanceService acceptanceService;

    @PostMapping("/create")
    @Operation(summary = "创建验收")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:create')")
    public CommonResult<Long> create(@Valid @RequestBody AcceptanceSaveReqVO createReqVO) {
        return success(acceptanceService.createAcceptance(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新验收")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody AcceptanceSaveReqVO updateReqVO) {
        acceptanceService.updateAcceptance(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除验收")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        acceptanceService.deleteAcceptance(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得验收分页")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:query')")
    public CommonResult<PageResult<AcceptanceRespVO>> getPage(@Validated AcceptancePageReqVO pageReqVO) {
        PageResult<AcceptanceDO> pageResult = acceptanceService.getAcceptancePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AcceptanceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得验收")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:query')")
    public CommonResult<AcceptanceRespVO> get(@RequestParam("id") Long id) {
        AcceptanceDO entity = acceptanceService.getAcceptance(id);
        return success(BeanUtils.toBean(entity, AcceptanceRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交验收（0草稿 → 1待提交）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        acceptanceService.submitAcceptance(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批验收（1待提交 → 2审批中）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:audit')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) {
        acceptanceService.approveAcceptance(id);
        return success(true);
    }

    @PutMapping("/pass")
    @Operation(summary = "通过验收（2审批中 → 3已通过，FR-ACC-005 交付件完整性门禁）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:audit')")
    public CommonResult<Boolean> pass(@RequestParam("id") Long id) {
        acceptanceService.passAcceptance(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回验收（2审批中 → 4已驳回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:audit')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) {
        acceptanceService.rejectAcceptance(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档验收（3已通过 → 5已归档）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-acceptance:audit')")
    public CommonResult<Boolean> archive(@RequestParam("id") Long id) {
        acceptanceService.archiveAcceptance(id);
        return success(true);
    }

}
