package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchCommand;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;

@Service
@RequiredArgsConstructor
public class CommerceAuthorityImportApplicationService {
    public static final String SCOPE = "POST:/api/v1/pms/commerce-authority/import-batches";

    private final CommerceAuthorityIngestApi authorityIngestApi;
    private final AuthorityPayloadCanonicalizer canonicalizer;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Transactional(rollbackFor = Exception.class)
    public CommerceAuthorityBatchResult execute(CommerceAuthorityBatchCommand command, Actor actor) {
        if (command == null || actor == null || actor.actorUserId() == null || actor.actorUserId() <= 0
                || !Objects.equals(command.tenantId(), actor.tenantId())) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_INVALID_ARGUMENT");
        }
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE, actor.actorUserId(), command.eventId()),
                canonicalizer.batchDigest(command), CommerceAuthorityBatchResult.class,
                () -> authorityIngestApi.ingestBatch(command),
                result -> successFacts(command, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PLATFORM_COMMAND_IN_PROGRESS);
        }
        if (execution.decision() != PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
            return execution.response();
        }
        return new CommerceAuthorityBatchResult(command.eventId(), command.batchId(),
                CommerceAuthorityBatchResult.Decision.EVENT_REPLAYED);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            CommerceAuthorityBatchCommand command, CommerceAuthorityBatchResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("eventId", command.eventId());
        detail.put("sourceSystem", command.sourceSystem());
        detail.put("batchId", command.batchId());
        detail.put("contractCount", command.contracts().size());
        detail.put("salesOrderCount", command.salesOrders().size());
        detail.put("salesOrderLineCount", command.orderLines().size());
        detail.put("orderContractRelationCount", command.orderContractRelations().size());
        detail.put("decision", result.decision().name());
        return new PlatformCommandExecutionApi.SuccessFacts(SCOPE, "CommerceAuthorityImportBatch",
                command.batchId(), command.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    public record Actor(Long tenantId, Long actorUserId) {
    }
}
