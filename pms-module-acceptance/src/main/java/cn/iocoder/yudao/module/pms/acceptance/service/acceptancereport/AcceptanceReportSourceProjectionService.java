package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitOutcome;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 验收报告 → 统一交付件投影（P06R I2）：报告版本事实是权威来源，以 AUTO_PROJECTION 提交
 * 落平台提交台账（requestKey=report:{版本ID}，载荷冻结 projectionKind/版本号），材料按工件版本登记。
 * 撤销走平台投影失效（材料置 INVALID 不再归档）。判定与数量由平台状态收敛承接。
 */
@Service
@RequiredArgsConstructor
public class AcceptanceReportSourceProjectionService {

    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableRuleApi rules;

    @Transactional(rollbackFor = Exception.class)
    public void project(AcceptanceReportVersionChangedMessage event) {
        validate(event);
        TemplateFrozenView view = platform.lockById(event.deliverableId())
                .filter(row -> Objects.equals(row.projectId(), event.projectId()))
                .orElseThrow(() -> new IllegalStateException("acceptance deliverable root unavailable"));
        if ("REVOKED".equals(event.changeType())) {
            revoke(event, view);
            return;
        }
        upsertCurrent(event, view);
    }

    private void upsertCurrent(AcceptanceReportVersionChangedMessage event, TemplateFrozenView view) {
        String requestKey = projectionKey(event.currentReportVersionId());
        // 同版本事件重放幂等（无论该投影现行与否——不可变投影事实不重复落账）。
        if (platform.findSubmissionByRequestKey(view.id(), requestKey).isPresent()) return;
        var current = platform.findCurrentSubmission(view.id());
        if ("EFFECTIVE".equals(event.changeType())) {
            if (current.isPresent()) throw new IllegalStateException("acceptance source current conflict");
        } else {
            // REPLACED：现行提交必须是先前报告版本的投影，链完整性由 requestKey 承载。
            if (current.isEmpty()) throw new IllegalStateException("acceptance source previous version missing");
            if (!PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(current.get().sourceType())
                    || !Objects.equals(projectionVersionId(current.get().requestKey()),
                    String.valueOf(event.previousReportVersionId()))) {
                throw new IllegalStateException("acceptance source current conflict");
            }
        }
        List<Long> materialIds = new ArrayList<>();
        for (FileArtifactVersionFact fact : event.attachments()) {
            materialIds.add(platform.registerProjectionFile(view.id(), fact, null,
                    PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION));
        }
        String payload = JsonUtils.toJsonString(Map.of("projectionKind", "ACCEPTANCE_REPORT",
                "reportVersionId", event.currentReportVersionId(), "reportVersionNo", event.reportVersionNo()));
        var outcome = platform.submitTemplateFrozen(new TemplateFrozenSubmitCommand(view.id(), requestKey,
                materialIds, PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, payload, null));
        backfillDecision(view, outcome);
    }

    private void revoke(AcceptanceReportVersionChangedMessage event, TemplateFrozenView view) {
        String requestKey = projectionKey(event.previousReportVersionId());
        if (platform.findSubmissionByRequestKey(view.id(), requestKey).isEmpty()) {
            throw new IllegalStateException("acceptance source current missing");
        }
        if (!platform.revokeProjectionSubmission(view.id(), requestKey,
                PlatformDeliveryRequirementApi.ARCHIVE_INVALID)) {
            // 已不是现行提交：重复撤销幂等通过；被更新版本取代后收到撤销属乱序冲突。
            if (platform.findCurrentSubmission(view.id()).isPresent()) {
                throw new IllegalStateException("acceptance source revoke conflict");
            }
        }
    }

    private void backfillDecision(TemplateFrozenView view, TemplateFrozenSubmitOutcome outcome) {
        if (outcome.replay()) return;
        var confirmation = rules.evaluate(view.projectId(), view.deliverableCode());
        platform.updateSubmissionDecision(outcome.submissionId(),
                JsonUtils.toJsonString(Map.of("confirmation", confirmation)));
    }

    static String projectionKey(Long reportVersionId) {
        return "report:" + reportVersionId;
    }

    static String projectionVersionId(String requestKey) {
        return requestKey != null && requestKey.startsWith("report:")
                ? requestKey.substring("report:".length()) : "";
    }

    private void validate(AcceptanceReportVersionChangedMessage event) {
        List<String> changes = List.of("EFFECTIVE", "REPLACED", "REVOKED");
        if (event == null || event.tenantId() == null || event.acceptanceId() == null || event.projectId() == null
                || event.deliverableId() == null || event.deliverableId() <= 0
                || !List.of("PRELIMINARY", "FINAL").contains(event.reportType())
                || !changes.contains(event.changeType()) || event.publisherActorUserId() == null
                || event.publisherActorUserId() <= 0
                || ("REVOKED".equals(event.changeType())
                ? event.currentReportVersionId() != null || event.previousReportVersionId() == null
                : event.currentReportVersionId() == null || event.reportVersionNo() == null
                || event.reportVersionNo() <= 0 || event.attachments().isEmpty()
                || event.attachments().stream().anyMatch(this::invalidAttachment))) {
            throw new IllegalArgumentException("invalid acceptance report version event");
        }
    }

    private boolean invalidAttachment(FileArtifactVersionFact fact) {
        return fact == null || fact.artifactId() == null || fact.artifactId() <= 0
                || fact.versionNo() == null || fact.versionNo() <= 0
                || fact.referenceKey() == null || fact.referenceKey().isBlank()
                || fact.fileFactVersion() == null || fact.scopeVersion() == null
                || fact.sha256() == null || fact.sha256().length() != 64;
    }
}
