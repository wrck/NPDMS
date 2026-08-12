package cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.service.deliverable.DeliverableService;
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
 * 管理后台 - PMS 阶段交付件归集 Controller（FR-ENG-027）。
 * <p>
 * 路径前缀 {@code /pms/eng-deliverable}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-deliverable:*}。
 * 归集版本不可覆盖：归集后关键字段不可修改，仅可作废。
 */
@Tag(name = "管理后台 - PMS 阶段交付件归集")
@RestController
@RequestMapping("/pms/eng-deliverable")
@Validated
public class DeliverableController {

    @Resource
    private DeliverableService deliverableService;

    @PostMapping("/create")
    @Operation(summary = "创建交付件（待归集状态）")
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:create')")
    public CommonResult<Long> createDeliverable(@Valid @RequestBody DeliverableSaveReqVO createReqVO) {
        return success(deliverableService.createDeliverable(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新交付件（已归集不可修改）")
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:update')")
    public CommonResult<Boolean> updateDeliverable(@Valid @RequestBody DeliverableSaveReqVO updateReqVO) {
        deliverableService.updateDeliverable(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除交付件（已归集不可删除）")
    @Parameter(name = "id", description = "交付件编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:delete')")
    public CommonResult<Boolean> deleteDeliverable(@RequestParam("id") Long id) {
        deliverableService.deleteDeliverable(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询交付件详情")
    @Parameter(name = "id", description = "交付件编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:query')")
    public CommonResult<DeliverableRespVO> getDeliverable(@RequestParam("id") Long id) {
        DeliverableDO entity = deliverableService.getDeliverable(id);
        return success(BeanUtils.toBean(entity, DeliverableRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询交付件")
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:query')")
    public CommonResult<PageResult<DeliverableRespVO>> getDeliverablePage(@Validated DeliverablePageReqVO pageReqVO) {
        PageResult<DeliverableDO> pageResult = deliverableService.getDeliverablePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DeliverableRespVO.class));
    }

    @PutMapping("/archive")
    @Operation(summary = "归集交付件（0待归集 → 1已归集，幂等：已归集直接返回）")
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:archive')")
    public CommonResult<Long> archive(@RequestParam("id") Long id,
                                      @RequestParam(value = "archivedBy", required = false) Long archivedBy) {
        return success(deliverableService.archive(id, archivedBy));
    }

    @PutMapping("/void")
    @Operation(summary = "作废交付件（0待归集 / 1已归集 → 2已作废）")
    @Parameter(name = "id", description = "交付件编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-deliverable:update')")
    public CommonResult<Boolean> voidDeliverable(@RequestParam("id") Long id) {
        deliverableService.voidDeliverable(id);
        return success(true);
    }
}
