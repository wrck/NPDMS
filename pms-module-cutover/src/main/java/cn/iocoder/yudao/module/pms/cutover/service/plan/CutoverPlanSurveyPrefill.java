package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SaveCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec.DecodedContent;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort.SourceFacts;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Copies only explicit predecessors into a mutable draft; submitted source rows stay immutable. */
@Component
@RequiredArgsConstructor
public class CutoverPlanSurveyPrefill {
    private final CutoverChecklistApplicationService checklists;
    private final FileArtifactApi files;

    public record Query(SaveCutoverPlanDraftCommand command, CutoverTaskDO task, CutoverPlanRevisionDO plan,
                        SourceFacts source, DecodedContent content) {}

    public DecodedContent fill(Query query) {
        var content = query.content();
        if (!"ONLINE_TEMPLATE_STANDARD".equals(content.rootSnapshot().path("editMode").asText())) return content;
        if (!content.rootSnapshot().path("overview").path("projectDescription").asText().isBlank()
                && content.rootSnapshot().path("overview").path("preTopologyFile").isObject()) return content;
        var source = query.source().snapshot();
        var command = query.command();
        CutoverChecklistView survey;
        try {
            survey = checklists.getView(new CutoverChecklistReadQuery(new CutoverChecklistContext(
                    command.tenantId(), command.actorId(), command.correlationId()), query.task().getId(), source.checklistId()));
        } catch (CutoverChecklistException failure) {
            // Draft saving does not require preceding stages to remain ready. Never import unavailable sources.
            if (Set.of(CutoverChecklistException.Code.NOT_FOUND, CutoverChecklistException.Code.FROZEN_CONFIGURATION_NOT_FOUND,
                    CutoverChecklistException.Code.FROZEN_CONFIGURATION_INVALID).contains(failure.getCode())) return content;
            throw failure;
        }
        if (!Objects.equals(survey.taskId(), query.task().getId()) || !Objects.equals(survey.checklistId(), source.checklistId())
                || !Objects.equals(survey.checklistVersion(), source.checklistVersion())) {
            throw failure("方案引用的调研版本不匹配");
        }
        if (!"SUBMITTED".equals(survey.status())) return content;
        ObjectNode root = (ObjectNode) content.rootSnapshot().deepCopy();
        ObjectNode overview = (ObjectNode) root.path("overview");
        if (overview.path("projectDescription").asText().isBlank()) {
            var descriptions = new ArrayList<String>();
            var background = answer(survey, "SURVEY_CUTOVER_BACKGROUND").path("value").path("backgroundDescription");
            var business = answer(survey, "SURVEY_BUSINESS_SUMMARY").path("value");
            if (background.isTextual() && !background.asText().isBlank()) descriptions.add("割接背景：" + background.asText());
            if (business.isTextual() && !business.asText().isBlank()) descriptions.add("涉及业务：" + business.asText());
            String description = String.join("\n", descriptions);
            if (description.length() <= 4000) overview.put("projectDescription", description);
        }
        if (overview.path("preTopologyFile").isMissingNode() || overview.path("preTopologyFile").isNull()) {
            survey.items().stream().filter(item -> item.applicable() && "SURVEY_CURRENT_TOPOLOGY".equals(item.stableItemKey())
                    && item.currentResult() != null && "MANUAL".equals(item.currentResult().resultSourceCode()))
                    .findFirst().ifPresent(item -> {
                        var file = parseAnswer(item).path("file");
                        if (file.isObject()) overview.set("preTopologyFile", attach(new Attachment(query, item, file, "preTopologyFile")));
                    });
        }
        return new DecodedContent(root, content.steps(), content.supportArrangements(), content.fileFact(), content.ownershipConfirmed());
    }

    private record Attachment(Query query, CutoverChecklistView.Item item, JsonNode file, String field) {}
    private JsonNode attach(Attachment attachment) {
        var query = attachment.query();
        var file = JsonUtils.parseObject(attachment.file().toString(), CutoverPlanFilePort.FileFact.class);
        var version = file.fileFactVersion();
        var original = new FileArtifactVersionRevalidationQuery(file.artifactId(), file.versionNo(), "CUT", "CUTOVER_CHECKLIST_ITEM",
                attachment.item().itemId().toString(), "MANUAL_EVIDENCE", file.referenceKey(), FileActionCodes.READ,
                new FileFactVersion(version.artifactVersion(), version.referenceVersion(), version.availabilityVersion()), file.scopeVersion());
        String targetKey = UUID.nameUUIDFromBytes(("cutover-survey:" + query.plan().getId() + ":" + attachment.field()
                + ":" + file.artifactId() + ":" + file.versionNo()).getBytes(StandardCharsets.UTF_8)).toString();
        var target = new ExistingFileReferenceTarget("CUT", "CUTOVER_PLAN", query.task().getId().toString(), "FULL_PLAN",
                targetKey, query.source().snapshot().projectScopeVersion());
        var attached = files.attachExistingVersions(new AttachExistingFileVersionsCommand("cutover-plan-survey:" + query.plan().getId(),
                List.of(new AttachExistingFileVersionItem(original, target))));
        if (attached.size() != 1) throw failure("方案附件绑定结果不完整");
        var fact = attached.getFirst();
        if (!Objects.equals(fact.artifactId(), file.artifactId()) || !Objects.equals(fact.versionNo(), file.versionNo())
                || !Objects.equals(fact.sha256(), file.sha256()) || !targetKey.equals(fact.referenceKey())
                || !Objects.equals(fact.scopeVersion(), target.expectedScopeVersion())
                || !"ACTIVE".equals(fact.referenceStatus()) || !"AVAILABLE".equals(fact.availabilityStatus())) throw failure("方案附件绑定事实不匹配");
        ObjectNode result = JsonUtils.getObjectMapper().valueToTree(CutoverPlanFilePort.FileFact.builder().artifactId(fact.artifactId())
                .versionNo(fact.versionNo()).referenceKey(fact.referenceKey()).sha256(fact.sha256()).scopeVersion(fact.scopeVersion())
                .fileFactVersion(new CutoverPlanFilePort.FileFactVersion(fact.fileFactVersion().artifactVersion(),
                        fact.fileFactVersion().referenceVersion(), fact.fileFactVersion().availabilityVersion())).build());
        if (fact.artifactId() >= 9_007_199_254_740_991L) result.put("artifactId", fact.artifactId().toString());
        if (fact.scopeVersion() >= 9_007_199_254_740_991L) result.put("scopeVersion", fact.scopeVersion().toString());
        return result;
    }

    private static JsonNode answer(CutoverChecklistView survey, String key) {
        return survey.items().stream().filter(item -> item.applicable() && key.equals(item.stableItemKey()))
                .findFirst().map(CutoverPlanSurveyPrefill::parseAnswer).orElseGet(() -> JsonUtils.getObjectMapper().createObjectNode());
    }
    private static JsonNode parseAnswer(CutoverChecklistView.Item item) {
        return item.currentResult() == null || item.currentResult().answerSnapshot() == null ? JsonUtils.getObjectMapper().createObjectNode()
                : JsonUtils.parseTree(item.currentResult().answerSnapshot());
    }
    private static CutoverPlanApplicationException failure(String message) {
        return new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.FILE_FACT_STALE, message);
    }
}
