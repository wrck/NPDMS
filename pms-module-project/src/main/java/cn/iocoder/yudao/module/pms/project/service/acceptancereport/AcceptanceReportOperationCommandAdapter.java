package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
@RequiredArgsConstructor
public class AcceptanceReportOperationCommandAdapter implements ProjectBusinessOperationCommandAdapter {
    private static final Set<String> CODES = Set.of("ACC.ACCEPTANCE_REPORT.CREATE_DRAFT", "ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT",
            "ACC.ACCEPTANCE_REPORT.PUBLISH", "ACC.ACCEPTANCE_REPORT.REVOKE");
    private final ObjectProvider<AcceptanceReportCommandService> commands;
    private final ObjectProvider<AcceptanceReportQueryService> queries;
    private final ObjectProvider<AcceptanceReportOperationAccessProvider> access;
    @Override public boolean supports(String code, int version) { return version == 1 && CODES.contains(code); }
    @Override public void authorizeReplay(String code, ProjectOperationCommand command) {
        if (!supports(code, 1)) throw exception(BAD_REQUEST, "OPERATION_NOT_REGISTERED");
        var allowed = access.getObject().inspect(new ProjectBusinessOperationAccessProvider.Context(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), command.projectId(), command.objectId()));
        if (!allowed.permittedOperations().contains(code)) throw exception(FORBIDDEN);
    }
    @Override public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        authorizeReplay(code, command);
        Long id;
        try { id = Long.valueOf(command.objectId()); } catch (RuntimeException invalid) { throw exception(BAD_REQUEST, "BUSINESS_OBJECT_REQUIRED"); }
        var actor = new AcceptanceReportCommands.Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), null);
        var queryActor = new AcceptanceReportQueryService.Actor(actor.tenantId(), actor.userId());
        var activity = queries.getObject().get(id, queryActor);
        if (!Objects.equals(activity.projectId(), command.projectId()) || !Objects.equals(activity.version(), command.expectedBusinessVersion()))
            throw exception(BAD_REQUEST, "BUSINESS_VERSION_CONFLICT");
        var input = command.input();
        if (input.has("execution") || input.has("tenantId")) throw exception(BAD_REQUEST, "UNTRUSTED_EXECUTION_INPUT");
        String key = "PROJECT_OP:" + DigestUtil.sha256Hex(code + ":" + command.nodeKind() + ":" + command.nodeId() + ":" + command.idempotencyKey());
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(command));
        AcceptanceReportCommands.ReportResult result;
        switch (code) {
            case "ACC.ACCEPTANCE_REPORT.CREATE_DRAFT" -> result = commands.getObject().createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                    id, command.expectedBusinessVersion(), JsonUtils.convertObject(input, AcceptanceReportCommands.DraftContent.class)), actor);
            case "ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT" -> result = commands.getObject().updateDraft(new AcceptanceReportCommands.UpdateDraftCommand(
                    id, requiredLong(input, "reportVersionId"), command.expectedBusinessVersion(), requiredInt(input, "expectedReportVersionNo"),
                    JsonUtils.convertObject(input, AcceptanceReportCommands.DraftContent.class)), actor);
            case "ACC.ACCEPTANCE_REPORT.PUBLISH" -> result = commands.getObject().publish(new AcceptanceReportCommands.PublishCommand(
                    id, requiredLong(input, "reportVersionId"), command.expectedBusinessVersion(), requiredInt(input, "expectedReportVersionNo"),
                    nullableLong(input, "expectedCurrentReportVersionId"), key, digest), actor);
            case "ACC.ACCEPTANCE_REPORT.REVOKE" -> result = commands.getObject().revoke(new AcceptanceReportCommands.RevokeCommand(
                    id, command.expectedBusinessVersion(), requiredLong(input, "expectedCurrentReportVersionId"),
                    requiredInt(input, "expectedCurrentReportVersionNo"), key, digest), actor);
            default -> throw exception(BAD_REQUEST, "OPERATION_NOT_REGISTERED");
        }
        var current = queries.getObject().get(id, queryActor);
        if (result == null || !id.equals(result.acceptanceId())) throw new IllegalStateException("OWNER_RESULT_IDENTITY_INVALID");
        String fact = "ACC:ACCEPTANCE:" + id + ":" + current.version();
        return new ProjectOperationResult("ACC", "ACCEPTANCE", id.toString(),
                result.reportVersionId() == null ? null : result.reportVersionId().toString(), current.version(), fact,
                code.endsWith(".PUBLISH") ? "REPORT_VERSION_PUBLISHED" : code.endsWith(".REVOKE") ? "REPORT_VERSION_REVOKED" : "REPORT_DRAFT_SAVED",
                JsonUtils.parseTree(JsonUtils.toJsonString(result)), result.replayed());
    }
    private static Long nullableLong(tools.jackson.databind.JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        try { long value = Long.parseLong(node.get(field).asText()); if (value > 0) return value; }
        catch (NumberFormatException invalid) { /* Explicit error below. */ }
        throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID");
    }
    private static Long requiredLong(tools.jackson.databind.JsonNode node, String field) {
        Long value = nullableLong(node, field); if (value == null) throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID"); return value;
    }
    private static Integer requiredInt(tools.jackson.databind.JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0)
            throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID");
        return value.intValue();
    }
}
