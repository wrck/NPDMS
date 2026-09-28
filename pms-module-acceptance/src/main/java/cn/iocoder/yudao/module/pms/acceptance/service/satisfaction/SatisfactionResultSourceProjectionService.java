package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionResultFactApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionResultFactQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitOutcome;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.event.SatisfactionResultVersionChangedMessage;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 满意度成果 → 统一交付件投影（P06R I2）：成果事实是权威来源，以 AUTO_PROJECTION 提交落
 * 平台提交台账（requestKey=satisfaction-result:{成果ID}:{成果版本}）。旧"非本来源占据 CURRENT 时
 * 让位落历史行"语义在统一台账下收敛为跳过（现行链不可并发并存）；证据已失效的事件不再投影。
 */
@Service
@RequiredArgsConstructor
public class SatisfactionResultSourceProjectionService {
    private final SatisfactionResultFactApi resultFactApi;
    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableRuleApi rules;

    @Transactional(rollbackFor = Exception.class)
    public void project(SatisfactionResultVersionChangedMessage event) {
        validate(event);
        // The event describes frozen Owner evidence, not a request to advance the current project task version.
        TemplateFrozenView view = platform.lockById(event.deliverableId())
                .filter(row -> Objects.equals(row.projectId(), event.projectId()))
                .orElseThrow(() -> new IllegalStateException("SATISFACTION_DELIVERABLE_ROOT_UNAVAILABLE"));
        if ("INVALIDATED".equals(event.changeType())) {
            invalidate(event, view);
        } else {
            record(event, view);
        }
    }

    private void record(SatisfactionResultVersionChangedMessage event, TemplateFrozenView view) {
        String requestKey = projectionKey(event.resultId(), event.resultVersion());
        if (platform.findSubmissionByRequestKey(view.id(), requestKey).isPresent()) return;
        SatisfactionResultFact resultFact = resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(
                event.tenantId(), event.resultId(), event.resultFactVersion()));
        if (!exactCurrentResult(event, resultFact)) return;
        var current = platform.findCurrentSubmission(view.id());
        if (current.isPresent()) {
            if (!PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(current.get().sourceType())) return;
            String kind = projectionKind(current.get().requestPayloadJson());
            // 与文档收集侧语义对齐：其他证据来源已占据 CURRENT 时让位，避免两个自动投影因
            // 调度先后不同互抛冲突、outbox 无限重试。
            if (!"SATISFACTION_RESULT".equals(kind)) return;
            long currentVersion = JsonUtils.parseTree(current.get().requestPayloadJson())
                    .path("resultVersion").asLong(0);
            if (currentVersion > event.resultVersion()) return;
            if (currentVersion == event.resultVersion()) {
                throw new IllegalStateException("SATISFACTION_SOURCE_VERSION_CONFLICT");
            }
        }
        List<Long> materialIds = new ArrayList<>();
        for (var file : event.files()) {
            materialIds.add(platform.registerProjectionFile(view.id(), toFact(file), null,
                    PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION));
        }
        String payload = JsonUtils.toJsonString(Map.of("projectionKind", "SATISFACTION_RESULT",
                "resultId", event.resultId(), "resultVersion", event.resultVersion()));
        var outcome = platform.submitTemplateFrozen(new TemplateFrozenSubmitCommand(view.id(), requestKey,
                materialIds, PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, payload, null));
        if (!outcome.replay()) {
            var confirmation = rules.evaluate(view.projectId(), view.deliverableCode());
            platform.updateSubmissionDecision(outcome.submissionId(),
                    JsonUtils.toJsonString(Map.of("confirmation", confirmation)));
        }
    }

    private void invalidate(SatisfactionResultVersionChangedMessage event, TemplateFrozenView view) {
        SatisfactionResultFact resultFact = resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(
                event.tenantId(), event.resultId(), event.resultFactVersion()));
        if (resultFact == null || !"FOUND".equals(resultFact.outcome())
                || !Objects.equals(resultFact.resultId(), event.resultId())
                || !Objects.equals(resultFact.resultVersion(), event.resultVersion())
                || !Objects.equals(resultFact.factVersion(), event.resultFactVersion())
                || !"INVALIDATED".equals(resultFact.resultStatus())) {
            throw new IllegalStateException("SATISFACTION_RESULT_INVALIDATION_FACT_CONFLICT");
        }
        String requestKey = projectionKey(event.resultId(), event.resultVersion());
        if (platform.findSubmissionByRequestKey(view.id(), requestKey).isEmpty()) {
            throw new IllegalStateException("SATISFACTION_SOURCE_INVALIDATION_PENDING_RECORDED");
        }
        // 满意度撤销保持材料待归档（历史证据仍归档），仅失效提交链。
        platform.revokeProjectionSubmission(view.id(), requestKey, null);
    }

    private boolean exactCurrentResult(SatisfactionResultVersionChangedMessage event, SatisfactionResultFact fact) {
        return fact != null && "FOUND".equals(fact.outcome()) && Objects.equals(fact.resultId(), event.resultId())
                && Objects.equals(fact.resultVersion(), event.resultVersion())
                && Objects.equals(fact.factVersion(), event.resultFactVersion())
                && fact.passed() && "EFFECTIVE".equals(fact.resultStatus());
    }

    static String projectionKey(Long resultId, long resultVersion) {
        return "satisfaction-result:" + resultId + ":" + resultVersion;
    }

    private static String projectionKind(String requestPayloadJson) {
        try {
            return JsonUtils.parseTree(requestPayloadJson == null ? "{}" : requestPayloadJson)
                    .path("projectionKind").asText("");
        } catch (Exception invalid) {
            return "";
        }
    }

    private static cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact toFact(
            SatisfactionResultVersionChangedMessage.FileFact file) {
        return new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact(
                file.artifactId(), file.versionNo(), file.referenceKey(), null, null, null, null,
                file.sha256(), "AVAILABLE", "ACTIVE",
                new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion(
                        file.artifactVersion(), file.referenceVersion(), file.availabilityVersion()),
                file.scopeVersion());
    }

    private void validate(SatisfactionResultVersionChangedMessage event) {
        if (event == null || event.tenantId() == null || event.projectId() == null
                || event.deliverableId() == null || event.deliverableId() <= 0
                || event.projectTaskId() == null || event.projectTaskVersion() == null
                || event.projectTaskVersion() < 0 || event.resultId() == null || event.resultVersion() == null
                || event.resultVersion() <= 0 || event.resultFactVersion() == null || event.resultFactVersion() < 0
                || !List.of("RECORDED", "INVALIDATED").contains(event.changeType())
                || event.archiveActorUserId() == null || event.archiveActorUserId() <= 0
                || ("RECORDED".equals(event.changeType()) && (event.files() == null || event.files().isEmpty()))
                || (event.files() != null && (event.files().stream().anyMatch(this::invalidFile)
                || !validSourceSequence(event.files())))) {
            throw new IllegalArgumentException("invalid satisfaction result event");
        }
    }

    private boolean invalidFile(SatisfactionResultVersionChangedMessage.FileFact file) {
        return file == null || file.sequence() == null || file.sequence() <= 0
                || file.sourceSequence() == null || file.sourceSequence() <= 0 || file.artifactId() == null
                || file.versionNo() == null || file.referenceKey() == null || file.referenceKey().isBlank()
                || file.scopeVersion() == null || file.sha256() == null || file.sha256().length() != 64;
    }

    private boolean validSourceSequence(List<SatisfactionResultVersionChangedMessage.FileFact> files) {
        List<Integer> actual = files.stream().map(SatisfactionResultVersionChangedMessage.FileFact::sourceSequence)
                .sorted().toList();
        List<Integer> expected = java.util.stream.IntStream.rangeClosed(1, files.size()).boxed().toList();
        return actual.equals(expected);
    }
}
