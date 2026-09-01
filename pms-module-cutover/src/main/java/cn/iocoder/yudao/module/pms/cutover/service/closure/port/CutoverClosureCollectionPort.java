package cn.iocoder.yudao.module.pms.cutover.service.closure.port;

import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AuthenticationMode;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.*;

/** CUT闭环对INT-12采集的消费端口；同一业务意图只能解析到一个外部任务。 */
public interface CutoverClosureCollectionPort {

    DispatchFact request(CollectionRequest request);

    DispatchLookup inspectByIntent(CollectionIntentIdentity identity);

    record CollectionIntentIdentity(Long tenantId, Long taskId, Long closureId, Long deviceId,
                                    CollectionStage collectionStage, String idempotencyKey) {
        public CollectionIntentIdentity {
            positive(tenantId, "tenantId");
            positive(taskId, "taskId");
            positive(closureId, "closureId");
            positive(deviceId, "deviceId");
            requireValue(collectionStage, "collectionStage");
            normalizedText(idempotencyKey, 128, "idempotencyKey");
        }
    }

    record CollectionRequest(CollectionIntentIdentity identity, Long actorId, Long projectId,
                             Authentication authentication, String templateCode, Long templateVersion,
                             String correlationId) {
        public CollectionRequest {
            requireValue(identity, "identity");
            positive(actorId, "actorId");
            positive(projectId, "projectId");
            requireValue(authentication, "authentication");
            normalizedText(templateCode, 64, "templateCode");
            nonNegative(templateVersion, "templateVersion");
            normalizedText(correlationId, 128, "correlationId");
        }
    }

    sealed interface Authentication permits SavedCredential, TransientCredential {
        AuthenticationMode mode();
    }

    record SavedCredential(Long credentialId, Long credentialVersion) implements Authentication {
        public SavedCredential {
            positive(credentialId, "credentialId");
            nonNegative(credentialVersion, "credentialVersion");
        }

        @Override
        public AuthenticationMode mode() {
            return AuthenticationMode.SAVED_CREDENTIAL;
        }
    }

    record TransientCredential(String loginName, String transientSecret,
                               boolean saveAsCredential) implements Authentication {
        public TransientCredential {
            normalizedText(loginName, 128, "loginName");
            CutoverClosureRules.transientSecret(transientSecret);
        }

        @Override
        public AuthenticationMode mode() {
            return AuthenticationMode.TRANSIENT_CREDENTIAL;
        }

        @Override
        public String toString() {
            return "TransientCredential[loginName=" + loginName
                    + ", transientSecret=<redacted>, saveAsCredential=" + saveAsCredential + "]";
        }
    }

    enum DispatchOutcome {
        ACCEPTED,
        FAILED
    }

    record DispatchFact(String collectionTaskId, DispatchOutcome outcome,
                        String failureCode, LocalDateTime occurredAt) {
        public DispatchFact {
            normalizedText(collectionTaskId, 128, "collectionTaskId");
            requireValue(outcome, "outcome");
            CutoverClosureRules.occurredAt(occurredAt);
            if (outcome == DispatchOutcome.FAILED) {
                normalizedText(failureCode, 64, "failureCode");
            } else {
                require(failureCode == null, "failureCode");
            }
        }
    }

    enum LookupStatus {
        FOUND,
        NOT_FOUND,
        UNKNOWN
    }

    record DispatchLookup(LookupStatus status, DispatchFact fact) {
        public DispatchLookup {
            requireValue(status, "status");
            require((status == LookupStatus.FOUND) == (fact != null), "lookupFact");
        }
    }
}
