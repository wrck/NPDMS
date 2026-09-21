package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Component
public class CollectionTaskStateMachine {

    public static boolean canCancelBeforeDispatch(String status, String technicalStage, String externalTaskId) {
        return externalTaskId == null && Set.of("CREATED", "AUTHORIZED").contains(status)
                && Set.of("PENDING_DISPATCH", "DISPATCHING", "RECONCILING").contains(technicalStage);
    }
    public static String rejectedBeforeDispatch(String status, String stage) {
        if (!Set.of("CREATED", "AUTHORIZED").contains(status) || !"PENDING_DISPATCH".equals(stage)) {
            throw new IllegalStateException("COLLECTION_TASK_NOT_PENDING_DISPATCH");
        }
        return "FAILED";
    }

    /** A provider acceptance is proof of technical dispatch only; terminal history is immutable. */
    public static String acceptedDispatchStatus(String currentStatus) {
        return switch (currentStatus) {
            case "CREATED", "AUTHORIZED" -> "DISPATCHED";
            case "DISPATCHED", "EXECUTING" -> currentStatus;
            default -> throw new IllegalStateException("COLLECTION_DISPATCH_STATE_INVALID");
        };
    }

    private static final Set<Status> IMMUTABLE_TERMINAL_STATUSES = Set.of(
            Status.COMPLETED,
            Status.FAILED,
            Status.CANCELLED,
            Status.SECURITY_EXCEPTION);

    public TaskSnapshot onTechnicalSuccess(TaskSnapshot task) {
        requireMutable(task);
        Status next = task.completionMode() == CompletionMode.CALLBACK_TERMINAL
                ? Status.COMPLETED : Status.RESULT_AVAILABLE;
        return task.withStatus(next);
    }

    public TaskSnapshot onExternalTerminal(TaskSnapshot task, String externalStatus, Long resultVersion) {
        requireMutable(task);
        if (!"SUCCESS".equals(externalStatus) && !"PARTIAL_SUCCESS".equals(externalStatus)) {
            throw new IllegalStateException("COLLECTION_EXTERNAL_STATUS_NOT_SUCCESS");
        }
        if (resultVersion == null || resultVersion <= 0) {
            throw new IllegalStateException("COLLECTION_RESULT_VERSION_INVALID");
        }
        return onTechnicalSuccess(task.withResult(resultVersion, externalStatus));
    }

    public TaskSnapshot onConsumed(TaskSnapshot task, ConsumerConfirmation confirmation) {
        if (task.status() != Status.RESULT_AVAILABLE) {
            throw new IllegalStateException("COLLECTION_RESULT_NOT_AVAILABLE");
        }
        if (confirmation == null
                || !Objects.equals(task.consumerContext(), confirmation.consumerContext())
                || !Objects.equals(task.consumerObjectType(), confirmation.consumerObjectType())
                || !Objects.equals(task.consumerObjectId(), confirmation.consumerObjectId())) {
            throw new IllegalStateException("COLLECTION_CONSUMER_MISMATCH");
        }
        if (!Objects.equals(task.resultVersion(), confirmation.resultVersion())) {
            throw new IllegalStateException("COLLECTION_RESULT_VERSION_MISMATCH");
        }
        return task.withStatus(Status.COMPLETED);
    }

    private static void requireMutable(TaskSnapshot task) {
        if (task == null) {
            throw new IllegalArgumentException("采集任务不能为空");
        }
        if (IMMUTABLE_TERMINAL_STATUSES.contains(task.status())) {
            throw new IllegalStateException("COLLECTION_TASK_TERMINAL");
        }
    }

    public enum Status {
        CREATED,
        AUTHORIZED,
        DISPATCHED,
        EXECUTING,
        CALLBACK_PROCESSING,
        RESULT_AVAILABLE,
        CONSUMED,
        COMPLETED,
        FAILED,
        CANCELLED,
        SECURITY_EXCEPTION
    }

    public enum CompletionMode {
        BUSINESS_CONSUMPTION,
        CALLBACK_TERMINAL
    }

    public record TaskSnapshot(
            Status status,
            CompletionMode completionMode,
            Long resultVersion,
            String consumerContext,
            String consumerObjectType,
            String consumerObjectId,
            String technicalResult) {

        public TaskSnapshot withStatus(Status nextStatus) {
            return new TaskSnapshot(nextStatus, completionMode, resultVersion, consumerContext,
                    consumerObjectType, consumerObjectId, technicalResult);
        }

        public TaskSnapshot withResult(Long nextResultVersion, String nextTechnicalResult) {
            return new TaskSnapshot(status, completionMode, nextResultVersion, consumerContext,
                    consumerObjectType, consumerObjectId, nextTechnicalResult);
        }
    }

    public record ConsumerConfirmation(
            String consumerContext,
            String consumerObjectType,
            String consumerObjectId,
            Long resultVersion) {
    }
}
