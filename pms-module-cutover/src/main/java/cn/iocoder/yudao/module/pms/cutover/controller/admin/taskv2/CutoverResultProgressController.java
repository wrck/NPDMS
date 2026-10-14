package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetCutoverResultApi;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class CutoverResultProgressController {
    private final CutoverTaskRequestContext context;
    private final CutoverTaskMapper tasks;
    private final CutoverProjectScopePort scopes;
    private final AssetCutoverResultApi assets;
    private final CutoverCoverageApi coverage;
    @GetMapping("/api/v1/pms/cutover-tasks/project-coverage")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverCoverageApi.Coverage> project(@RequestParam("projectId") Long projectId) {
        var actor = context.current();
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("项目身份无效");
        var scope = scopes.inspect(actor.actorId(), projectId, "ACTION_VIEW");
        if (scope == null || !scope.allowed()) throw new org.springframework.security.access.AccessDeniedException("无权查看该项目割接");
        return CommonResult.success(coverage.inspect(new CutoverCoverageApi.Query(actor.tenantId(), projectId)));
    }
    @GetMapping("/api/v1/pms/cutover-tasks/{taskId}/result-progress")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<Progress> get(@PathVariable("taskId") Long taskId) {
        var actor = context.current(); var task = tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), actor.tenantId())) throw new IllegalArgumentException("割接任务不存在");
        if (!scopes.inspect(actor.actorId(), task.getProjectId(), "ACTION_VIEW").allowed()) throw new SecurityException("无权查看该项目割接");
        return CommonResult.success(new Progress(assets.byTask(new AssetCutoverResultApi.Query(actor.tenantId(), task.getProjectId(), taskId)),
                coverage.inspect(new CutoverCoverageApi.Query(actor.tenantId(), task.getProjectId()))));
    }
    public record Progress(List<AssetCutoverResultApi.Receipt> assetReceipts, CutoverCoverageApi.Coverage projectCoverage) { }
}
