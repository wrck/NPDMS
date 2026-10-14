package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanContactPolicy;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequiredArgsConstructor
public class CutoverContactChoicesController {
    private final CutoverTaskRequestContext context;
    private final CutoverTaskMapper tasks;
    private final CutoverPlanContactPolicy contacts;
    @GetMapping("/api/v1/pms/cutover-tasks/{taskId}/project-contacts")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<List<ProjectContactReferenceApi.Reference>> list(@PathVariable("taskId") Long taskId) {
        var actor = context.current(); var task = tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), actor.tenantId())) throw new IllegalArgumentException("割接任务不存在");
        return CommonResult.success(contacts.list(actor.tenantId(), actor.actorId(), task.getProjectId()));
    }
}
