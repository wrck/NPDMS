package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.task.CutoverTaskIntakeApiImpl;
import cn.iocoder.yudao.module.pms.cutover.api.task.CutoverTaskIntakeApi;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeCommand;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

/** 项目入口使用项目可信执行上下文；后续业务仍由独立 CUT 工作台维护。 */
@Component
public class CutoverProjectOperationAdapter implements ProjectBusinessOperationProvider,
        ProjectBusinessOperationCommandAdapter, ProjectBusinessOperationAccessProvider {
    public static final String CREATE = "CUT.CUTOVER_TASK.CREATE";
    private final CutoverTaskIntakeApi intake;
    private final CutoverTaskMapper tasks;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    public CutoverProjectOperationAdapter(@Lazy CutoverTaskIntakeApi intake, CutoverTaskMapper tasks,
                                          ProjectScopeApi scopes, PermissionApi permissions) {
        this.intake = intake;
        this.tasks = tasks;
        this.scopes = scopes;
        this.permissions = permissions;
    }
    @Override public String ownerContext() { return "CUT"; }
    @Override public String objectType() { return "CUTOVER_TASK"; }
    @Override public List<ProjectBusinessOperationDescriptor> operations() {
        return List.of(new ProjectBusinessOperationDescriptor(CREATE, 1, ownerContext(), objectType(), "发起项目割接", "CREATE",
                Set.of("PRE", "POST"), CutoverTaskIntakeApiImpl.class, "create"));
    }
    @Override public Map<String, ProjectOperationControlScope> controlScopes() { return Map.of(CREATE, ProjectOperationControlScope.PROJECT_ENTRY_ONLY); }
    @Override public Map<String, String> permissionCodes() { return Map.of(CREATE, "pms:cutover-task:create"); }
    @Override public boolean supports(String code, int version) { return CREATE.equals(code) && version == 1; }
    @Override public Access inspect(Context context) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId()) || context.actorId() == null
                || !permissions.hasAnyPermissions(context.actorId(), "pms:cutover-task:query")) throw denied();
        requireScope(ProjectCurrentScopeQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.actorId())
                .anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        String factVersion = null;
        if (context.objectId() != null) {
            var task = tasks.selectById(Long.valueOf(context.objectId()));
            if (task == null || !Objects.equals(task.getTenantId(), context.tenantId()) || !Objects.equals(task.getProjectId(), context.projectId())) throw denied();
            factVersion = "CUT:CUTOVER_TASK:" + task.getId() + ":" + task.getVersion();
        }
        var edit = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.actorId())
                .anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_EDIT).build());
        return new Access(context.objectId() == null && edit.fullProjectIds().contains(context.projectId())
                && permissions.hasAnyPermissions(context.actorId(), "pms:cutover-task:create") ? Set.of(CREATE) : Set.of(), factVersion);
    }
    @Override public void authorizeReplay(String code, ProjectOperationCommand command) {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (!supports(code, 1) || command == null || actor == null || !permissions.hasAnyPermissions(actor, "pms:cutover-task:create")) throw denied();
        requireScope(ProjectCurrentScopeQuery.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .subjectUserId(actor)
                .anchorProjectId(command.projectId())
                .actionCode(ProjectScopeApi.ACTION_EDIT).build());
    }
    @Override public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        authorizeReplay(code, command);
        if (command.objectId() != null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) throw new IllegalArgumentException("割接创建身份无效");
        var input = ProjectOperationInput.read(JsonUtils.getObjectMapper(), command.input(), Input.class);
        String eventId = "project-cutover:" + command.projectId() + ":" + org.springframework.util.DigestUtils.md5DigestAsHex(
                command.idempotencyKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeResult result;
        try { result = intake.create(new CutoverTaskIntakeCommand("PROJECT_EVENT", command.projectId(), input.serialNumbers(),
                SecurityFrameworkUtils.getLoginUserId(), input.configurationCode(), input.taskName(), input.background(),
                input.cutoverType(), input.networkMode(), input.scheduledTime(), command.idempotencyKey(), null, null, eventId));
        } catch (cn.iocoder.yudao.module.pms.cutover.api.task.CutoverTaskIntakeException failure) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(1_011_005_122, failure.getMessage());
        }
        return new ProjectOperationResult(ownerContext(), objectType(), result.taskId().toString(), null,
                result.version(), "CUT:CUTOVER_TASK:" + result.taskId() + ":" + result.version(), "CUTOVER_CREATED",
                JsonUtils.parseTree(JsonUtils.toJsonString(result)), result.replayed());
    }
    public record Input(List<String> serialNumbers, String configurationCode, String taskName, String background,
                        String cutoverType, String networkMode, LocalDateTime scheduledTime) { }
    private void requireScope(ProjectCurrentScopeQuery query) {
        if (query.anchorProjectId() == null || !scopes.resolveCurrent(query)
                .fullProjectIds().contains(query.anchorProjectId())) throw denied();
    }
    private static SecurityException denied() { return new SecurityException("无权操作该项目割接"); }
}
