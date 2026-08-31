package cn.iocoder.yudao.module.pms.cutover.api.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeCommand;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;

import java.util.Set;

/** CUT内部接入Provider；由Task 2在正式Owner依赖齐备后注册为生产Bean。 */
public class CutoverTaskIntakeApiImpl implements CutoverTaskIntakeApi {

    private static final Set<String> INTERNAL_SOURCES = Set.of("ITR", "PROJECT_EVENT");

    private final CutoverTaskApplicationService applicationService;

    public CutoverTaskIntakeApiImpl(CutoverTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public CutoverTaskIntakeResult create(CutoverTaskIntakeCommand command) {
        requireCommand(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String idempotencyKey = "ITR".equals(command.sourceType())
                ? command.sourceSystem() + ":" + command.sourceBusinessNo()
                : command.businessEventId();
        try {
            CutoverTaskCommandResult result = applicationService.create(new CreateCutoverTaskCommand(
                    tenantId, command.handlingEngineerUserId(), idempotencyKey, command.correlationId(),
                    command.sourceType(), command.projectId(), command.serialNumbers(), command.configurationCode(),
                    command.taskName(),
                    command.background(), command.cutoverType(), command.networkMode(), command.scheduledTime(),
                    command.sourceSystem(), command.sourceBusinessNo(), command.businessEventId(), null));
            return new CutoverTaskIntakeResult(result.taskId(), result.taskNo(), result.currentStage(),
                    result.taskStatus(), result.version(), result.replayed());
        } catch (CutoverTaskApplicationException exception) {
            throw map(exception);
        }
    }

    private static void requireCommand(CutoverTaskIntakeCommand command) {
        if (command == null || !INTERNAL_SOURCES.contains(command.sourceType())
                || command.handlingEngineerUserId() == null || command.handlingEngineerUserId() <= 0) {
            throw new CutoverTaskIntakeException(CutoverTaskIntakeException.Code.INVALID_REQUEST,
                    "内部接入命令非法");
        }
    }

    private static CutoverTaskIntakeException map(CutoverTaskApplicationException exception) {
        CutoverTaskIntakeException.Code code = switch (exception.code()) {
            case INVALID_REQUEST -> CutoverTaskIntakeException.Code.INVALID_REQUEST;
            case CONFIGURATION_CONFLICT -> CutoverTaskIntakeException.Code.CONFIGURATION_CONFLICT;
            case DATA_SCOPE_FORBIDDEN, NOT_FOUND, PROJECT_SCOPE_STALE, PROJECT_CONTEXT_STALE, DEVICE_SCOPE_STALE ->
                    CutoverTaskIntakeException.Code.DATA_SCOPE_FORBIDDEN;
            case ACTIVE_DEVICE_CONFLICT, STATE_CONFLICT, VERSION_CONFLICT ->
                    CutoverTaskIntakeException.Code.ACTIVE_DEVICE_CONFLICT;
            case READINESS_NOT_READY, IMPLEMENTATION_READINESS_STALE ->
                    CutoverTaskIntakeException.Code.READINESS_NOT_READY;
            case CUSTOMER_CONTEXT_INVALID, CUSTOMER_SERVICE_LEVEL_STALE ->
                    CutoverTaskIntakeException.Code.CUSTOMER_CONTEXT_INVALID;
            case PROJ_PROVIDER_UNAVAILABLE, AST_PROVIDER_UNAVAILABLE, CUS_PROVIDER_UNAVAILABLE,
                    IMP_PROVIDER_UNAVAILABLE ->
                    CutoverTaskIntakeException.Code.OWNER_PROVIDER_UNAVAILABLE;
            case BUSINESS_GATE_INVALID -> CutoverTaskIntakeException.Code.INVALID_REQUEST;
            case IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS ->
                    CutoverTaskIntakeException.Code.SOURCE_IDENTITY_CONFLICT;
        };
        return new CutoverTaskIntakeException(code, exception.getMessage(), exception);
    }
}
