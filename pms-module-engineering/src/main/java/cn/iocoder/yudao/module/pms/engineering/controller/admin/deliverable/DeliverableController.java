package cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSummaryItemVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.service.deliverable.DeliverableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 阶段交付件历史查询 Controller（原 FR-ENG-027 归集入口）。
 * <p>
 * 路径前缀 {@code /pms/imp-deliverable}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:imp-deliverable:*}。
 * P06R 统一交付件后只读：创建/更新/删除/归集/作废端点已退役，
 * 新写入一律经统一交付件能力（plt_delivery_material），本表保留为不可变历史。
 */
@Tag(name = "管理后台 - PMS 阶段交付件历史查询")
@RestController
@RequestMapping("/pms/imp-deliverable")
@Validated
public class DeliverableController {

    @Resource
    private DeliverableService deliverableService;

    @GetMapping("/get")
    @Operation(summary = "查询历史交付件详情")
    @Parameter(name = "id", description = "交付件编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-deliverable:query')")
    public CommonResult<DeliverableRespVO> getDeliverable(@RequestParam("id") Long id) {
        DeliverableDO entity = deliverableService.getDeliverable(id);
        return success(BeanUtils.toBean(entity, DeliverableRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询历史交付件")
    @PreAuthorize("@ss.hasPermission('pms:imp-deliverable:query')")
    public CommonResult<PageResult<DeliverableRespVO>> getDeliverablePage(@Validated DeliverablePageReqVO pageReqVO) {
        PageResult<DeliverableDO> pageResult = deliverableService.getDeliverablePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DeliverableRespVO.class));
    }

    @GetMapping("/project-summary")
    @Operation(summary = "按项目交付件汇总（6.4：统一交付件材料 + ACC 归档事实 + 历史归集行）")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-deliverable:query')")
    public CommonResult<List<DeliverableSummaryItemVO>> getProjectSummary(@RequestParam("projectId") Long projectId) {
        return success(deliverableService.getProjectSummary(projectId));
    }
}
