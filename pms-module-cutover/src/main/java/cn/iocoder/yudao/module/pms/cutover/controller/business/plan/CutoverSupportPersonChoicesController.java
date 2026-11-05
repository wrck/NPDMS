package cn.iocoder.yudao.module.pms.cutover.controller.business.plan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.CutoverTaskRequestContext;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverSupportPersonPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class CutoverSupportPersonChoicesController {
    private final CutoverTaskRequestContext context;
    private final CutoverTaskMapper tasks;
    private final CutoverProjectScopePort scopes;
    private final CutoverSupportPersonPolicy people;
    @GetMapping("/api/v1/pms/cutover-tasks/{taskId}/support-person-choices")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<List<Choice>> list(@PathVariable Long taskId, @RequestParam String roleCode) {
        if (taskId == null || taskId <= 0 || !Set.of("CUSTOMER", "DP_FIRST_LINE", "DP_SECOND_LINE", "DP_RND").contains(roleCode))
            throw new IllegalArgumentException("保障角色或任务编号无效");
        var actor = context.current(); var task = tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), actor.tenantId()) || !"NEW_PLATFORM".equals(task.getTaskOrigin())) throw new SecurityException("割接任务不可见");
        var scope = scopes.inspect(actor.actorId(), task.getProjectId(), "ACTION_VIEW");
        if (scope == null || !scope.allowed()) throw new SecurityException("项目范围不可见");
        return CommonResult.success(people.choices(new CutoverSupportPersonPolicy.Query(actor.tenantId(), actor.actorId(), task.getProjectId(), roleCode)).stream()
                .map(choice -> new Choice(new Reference(choice.reference().sourceCode(), choice.reference().id().toString(), choice.reference().version().toString()), choice.name(), choice.phone(), choice.username(), choice.email())).toList());
    }
    public record Reference(String sourceCode, String id, String version) {}
    public record Choice(Reference reference, String name, String phone, String username, String email) {}

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> forbidden(Exception exception) {
        return failure(new CutoverPlanContractException(403, 1_011_009_101, exception.getMessage(),
                CutoverPlanContractException.ErrorData.builder().category("FUNCTION_OR_SCOPE_DENIED")
                        .reasonCode("PROJECT_OR_TASK_SCOPE_DENIED").recoveryAction("CONTACT_ADMIN").build()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> invalid(Exception exception) {
        return failure(new CutoverPlanContractException(400, 1_011_009_100, exception.getMessage(),
                CutoverPlanContractException.ErrorData.builder().category("INVALID_REQUEST")
                        .reasonCode("REQUEST_SCHEMA_INVALID").recoveryAction("FIX_REQUEST").build()));
    }

    private static ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> failure(CutoverPlanContractException exception) {
        CommonResult<CutoverPlanContractException.ErrorData> result = CommonResult.error(exception.errorCode(), exception.getMessage());
        result.setData(exception.errorData());
        return ResponseEntity.status(exception.httpStatus()).body(result);
    }
}
