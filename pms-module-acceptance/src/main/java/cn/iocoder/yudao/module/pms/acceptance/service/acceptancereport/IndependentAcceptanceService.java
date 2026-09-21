package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.*;

@Service @RequiredArgsConstructor
public class IndependentAcceptanceService {
    private final AcceptanceActivityMapper activities;
    private final ProjectAcceptanceContextApi projects;
    private final PlatformCommandExecutionApi commands;
    private final PermissionApi permissions;

    public ProjectAcceptanceContextApi.Context context(Long projectId, AcceptanceReportCommands.Actor actor) {
        requireActor(actor);
        return projects.inspect(new ProjectAcceptanceContextApi.Query(actor.tenantId(), projectId, actor.userId()));
    }
    public Result create(Create command, String key, AcceptanceReportCommands.Actor actor) {
        if (command == null || key == null || key.isBlank() || key.length() > 128
                || !Set.of("PRELIMINARY", "FINAL").contains(String.valueOf(command.acceptanceType())))
            throw exception(ACC_REPORT_STATE_INVALID);
        context(command.projectId(), actor);
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(),
                        "POST:/pms/acceptances/independent", actor.userId(), key), digest(command), Result.class,
                () -> createOnce(command, key, actor), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "INDEPENDENT_ACCEPTANCE_CREATED", "AcceptanceActivity", result.acceptanceId().toString(),
                        actor.correlationId(), JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.response() == null || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS)
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }
    private Result createOnce(Create command, String key, AcceptanceReportCommands.Actor actor) {
        var context = projects.lock(new ProjectAcceptanceContextApi.Query(actor.tenantId(), command.projectId(), actor.userId()),
                command.expectedProjectVersion(), command.expectedTreeVersion());
        if (!"ACTIVE".equals(context.lifecycleStatus())) throw exception(ACC_REPORT_STATE_INVALID);
        var existing = activities.selectByIdentityForUpdate(new AcceptanceActivityIdentityLockQuery(
                actor.tenantId(), context.projectId(), command.acceptanceType()));
        if (existing != null) return new Result(existing.getId(), existing.getProjectId(), existing.getAcceptanceType(), false);
        var row = new AcceptanceActivityDO(); row.setId(IdWorker.getId()); row.setTenantId(actor.tenantId());
        row.setProjectId(context.projectId()); row.setAcceptanceType(command.acceptanceType());
        row.setActivityStatus("PENDING"); row.setVersion(0); row.setOriginKind("DIRECT");
        row.setOriginKey(actor.userId() + ":" + key);
        row.setOriginSnapshot(JsonUtils.toJsonString(Map.of("projectId", context.projectId(), "rootProjectId", context.rootProjectId(),
                "projectVersion", context.projectVersion(), "treeVersion", context.treeVersion(), "actorId", actor.userId())));
        row.setRuleSnapshot(IndependentAcceptancePolicy.SNAPSHOT);
        row.setCreator(actor.userId().toString()); row.setUpdater(actor.userId().toString());
        if (activities.insert(row) != 1) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        return new Result(row.getId(), row.getProjectId(), row.getAcceptanceType(), true);
    }
    private void requireActor(AcceptanceReportCommands.Actor actor) {
        if (actor == null || !Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId())
                || actor.userId() == null || actor.userId() <= 0
                || !permissions.hasAnyPermissions(actor.userId(), "pms:acceptance:report:write"))
            throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
    }
    private String digest(Create command) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonUtils.toJsonString(command).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    public record Create(Long projectId, String acceptanceType, Integer expectedProjectVersion, Long expectedTreeVersion) { }
    public record Result(Long acceptanceId, Long projectId, String acceptanceType, boolean created) { }
}
