package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityWriteApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;

@Service
@RequiredArgsConstructor
public class CommerceAuthorityImportApplicationService {

    public static final String SCOPE = "POST:/api/v1/pms/commerce-authority/import-batches";

    private final CommerceAuthorityWriteApi authorityWriteApi;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Transactional(rollbackFor = Exception.class)
    public AuthorityWriteResult execute(CommerceAuthorityWriteCommand command, Actor actor) {
        CommerceAuthorityWriteCommand normalized = normalize(command, actor);
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE, actor.actorUserId(), normalized.operationId()),
                digest(normalized), AuthorityWriteResult.class,
                () -> authorityWriteApi.apply(normalized),
                result -> successFacts(normalized, actor, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PLATFORM_COMMAND_IN_PROGRESS);
        }
        AuthorityWriteResult result = execution.response();
        if (execution.decision() != PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
            return result;
        }
        return new AuthorityWriteResult(result.sourceBatchId(), true, result.contractCount(),
                result.salesOrderCount(), result.salesOrderLineCount());
    }

    private CommerceAuthorityWriteCommand normalize(CommerceAuthorityWriteCommand command, Actor actor) {
        if (command == null || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorUserId() == null || actor.actorUserId() <= 0
                || blank(command.sourceBatchId()) || blank(command.operationId())
                || !actor.tenantId().equals(command.tenantId())) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_INVALID_ARGUMENT");
        }
        List<CommerceAuthorityWriteCommand.ContractSourceRecord> contracts = safe(command.contracts()).stream()
                .map(this::normalize).sorted(Comparator.comparing(
                        CommerceAuthorityWriteCommand.ContractSourceRecord::sourceRecordKey)
                        .thenComparing(CommerceAuthorityWriteCommand.ContractSourceRecord::sourceVersion))
                .toList();
        List<CommerceAuthorityWriteCommand.SalesOrderSourceRecord> orders = safe(command.salesOrders()).stream()
                .map(this::normalize).sorted(Comparator.comparing(
                        CommerceAuthorityWriteCommand.SalesOrderSourceRecord::sourceRecordKey)
                        .thenComparing(CommerceAuthorityWriteCommand.SalesOrderSourceRecord::sourceVersion))
                .toList();
        List<CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord> lines = safe(command.salesOrderLines()).stream()
                .map(this::normalize).sorted(Comparator.comparing(
                        CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord::sourceRecordKey)
                        .thenComparing(CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord::sourceVersion))
                .toList();
        if (contracts.isEmpty() && orders.isEmpty() && lines.isEmpty()) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_EMPTY_BATCH");
        }
        return new CommerceAuthorityWriteCommand(actor.tenantId(), required(command.sourceBatchId()),
                required(command.operationId()), contracts, orders, lines);
    }

    private CommerceAuthorityWriteCommand.ContractSourceRecord normalize(
            CommerceAuthorityWriteCommand.ContractSourceRecord value) {
        return new CommerceAuthorityWriteCommand.ContractSourceRecord(
                required(value.sourceSystem()), required(value.sourceRecordKey()), required(value.sourceVersion()),
                required(value.companyCode()), required(value.contractNo()), optional(value.contractName()),
                required(value.status()), value.sourceUpdatedAt());
    }

    private CommerceAuthorityWriteCommand.SalesOrderSourceRecord normalize(
            CommerceAuthorityWriteCommand.SalesOrderSourceRecord value) {
        return new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                required(value.sourceSystem()), required(value.sourceRecordKey()), required(value.sourceVersion()),
                required(value.companyCode()), required(value.orderType()), required(value.orderNo()),
                required(value.status()), value.sourceUpdatedAt());
    }

    private CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord normalize(
            CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord value) {
        return new CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord(
                required(value.sourceSystem()), required(value.sourceRecordKey()), required(value.sourceVersion()),
                required(value.orderSourceRecordKey()), required(value.lineNo()), required(value.itemCode()),
                optional(value.itemDescription()), optional(value.productCode()),
                decimal(value.orderQuantity()), decimal(value.openQuantity()), decimal(value.deliveredQuantity()),
                required(value.unitCode()), value.unitScale(), required(value.quantityStatus()),
                required(value.status()), value.sourceUpdatedAt());
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            CommerceAuthorityWriteCommand command, Actor actor, AuthorityWriteResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operationId", command.operationId());
        detail.put("sourceSystem", sourceSystem(command));
        detail.put("sourceBatchId", command.sourceBatchId());
        detail.put("contractCount", result.contractCount());
        detail.put("salesOrderCount", result.salesOrderCount());
        detail.put("salesOrderLineCount", result.salesOrderLineCount());
        return new PlatformCommandExecutionApi.SuccessFacts(SCOPE, "CommerceAuthorityImportBatch",
                command.sourceBatchId(), command.operationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private String sourceSystem(CommerceAuthorityWriteCommand command) {
        return !command.contracts().isEmpty() ? command.contracts().getFirst().sourceSystem()
                : !command.salesOrders().isEmpty() ? command.salesOrders().getFirst().sourceSystem()
                : command.salesOrderLines().getFirst().sourceSystem();
    }

    private String digest(CommerceAuthorityWriteCommand command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private BigDecimal decimal(BigDecimal value) {
        if (value == null) return null;
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private String required(String value) {
        if (blank(value)) throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_REQUIRED_FIELD");
        return value.trim();
    }

    private String optional(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record Actor(Long tenantId, Long actorUserId) {
    }
}
