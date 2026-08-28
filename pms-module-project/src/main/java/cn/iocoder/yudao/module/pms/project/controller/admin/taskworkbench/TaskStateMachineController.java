package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.TaskStateMachineSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineDefinition;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskStateMachineService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.PublishTaskStateMachineCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 项目任务状态机")
@RestController
@RequestMapping("/api/v1/pms/project-task-state-machines")
@Validated
@RequiredArgsConstructor
public class TaskStateMachineController {

    private final TaskStateMachineService service;
    private final Environment environment;

    @GetMapping
    @Operation(summary = "查询当前发布任务状态机")
    @PreAuthorize("@ss.hasPermission('pms:project-task-state:manage')")
    public CommonResult<TaskStateMachineDefinition> getPublished() {
        return withTrustedTenant(() -> success(service.getPublished(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId())));
    }

    @PostMapping
    @Operation(summary = "创建任务状态机草稿")
    @PreAuthorize("@ss.hasPermission('pms:project-task-state:manage')")
    public CommonResult<TaskStateMachineDefinition> createDraft(
            @Valid @RequestBody TaskStateMachineSaveReqVO request) {
        return withTrustedTenant(() -> success(service.createDraft(request, actor())));
    }

    @PostMapping("/{id}/actions/publish")
    @Operation(summary = "发布任务状态机版本")
    @PreAuthorize("@ss.hasPermission('pms:project-task-state:manage')")
    public CommonResult<TaskStateMachineDefinition> publish(
            @PathVariable("id") Long revisionId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch) {
        int expectedVersion = parseIfMatch(ifMatch);
        return withTrustedTenant(() -> success(service.publish(new PublishTaskStateMachineCommand(
                revisionId, expectedVersion, idempotencyKey,
                digest(revisionId + ":" + expectedVersion)), actor())));
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    private int parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException ex) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
