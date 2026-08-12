package cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.project.service.deliverablechecklist.DeliverableChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 交付件检查")
@RestController
@RequestMapping("/pms/acc-deliverable-checklist")
@Validated
public class DeliverableChecklistController {

    @Resource
    private DeliverableChecklistService deliverableChecklistService;

    @PostMapping("/create")
    @Operation(summary = "创建交付件检查")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:create')")
    public CommonResult<Long> create(@Valid @RequestBody DeliverableChecklistSaveReqVO createReqVO) {
        return success(deliverableChecklistService.createDeliverableChecklist(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新交付件检查")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DeliverableChecklistSaveReqVO updateReqVO) {
        deliverableChecklistService.updateDeliverableChecklist(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除交付件检查")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        deliverableChecklistService.deleteDeliverableChecklist(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得交付件检查分页")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:query')")
    public CommonResult<PageResult<DeliverableChecklistRespVO>> getPage(@Validated DeliverableChecklistPageReqVO pageReqVO) {
        PageResult<DeliverableChecklistDO> pageResult = deliverableChecklistService.getDeliverableChecklistPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DeliverableChecklistRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得交付件检查")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:query')")
    public CommonResult<DeliverableChecklistRespVO> get(@RequestParam("id") Long id) {
        DeliverableChecklistDO entity = deliverableChecklistService.getDeliverableChecklist(id);
        return success(BeanUtils.toBean(entity, DeliverableChecklistRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交交付件检查（0草稿 → 1已提交）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        deliverableChecklistService.submitDeliverableChecklist(id);
        return success(true);
    }

    @PutMapping("/pass")
    @Operation(summary = "通过交付件检查（1已提交 → 2已通过）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:audit')")
    public CommonResult<Boolean> pass(@RequestParam("id") Long id) {
        deliverableChecklistService.passDeliverableChecklist(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回交付件检查（1已提交 → 3已驳回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-deliverable-checklist:audit')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) {
        deliverableChecklistService.rejectDeliverableChecklist(id);
        return success(true);
    }

}
