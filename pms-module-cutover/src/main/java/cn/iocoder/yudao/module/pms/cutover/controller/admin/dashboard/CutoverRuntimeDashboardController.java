package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverRuntimeDashboardService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/cutover-dashboard")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
public class CutoverRuntimeDashboardController {
    private final CutoverRuntimeDashboardService service;
    @GetMapping("/kpis")
    public CommonResult<CutoverDashboardKpiView> kpis() { return success(service.kpis()); }
    @GetMapping("/tasks")
    public CommonResult<PageResult<CutoverTaskViews.Summary>> tasks(@RequestParam("category") String category,
            @RequestParam(value = "pageNo", defaultValue = "1") int page, @RequestParam(value = "pageSize", defaultValue = "20") int size) {
        return success(service.page(category, page, size));
    }
}
