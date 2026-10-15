package cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.vo;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistMatchingOptions;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy;
import java.util.List;

/** 明确的HTTP响应合同；字段保持现行业务投影，不暴露Service内部记录类型。 */
public final class CutoverChecklistResponses {
    private CutoverChecklistResponses() { }

    public record View(Long taskId, String taskStage, Long taskVersion,
            Long projectScopeVersion, Long checklistId, Long checklistVersion,
            Long checklistFactVersion, String status, String inputSnapshotHash,
            String configRevisionSnapshot, String matchTrace, String configGapSnapshot,
            List<Item> items) {
        public static View from(CutoverChecklistView value) {
            if (value == null) return null;
            return new View(value.taskId(), value.taskStage(), value.taskVersion(),
                    value.projectScopeVersion(), value.checklistId(), value.checklistVersion(),
                    value.checklistFactVersion(), value.status(), value.inputSnapshotHash(),
                    value.configRevisionSnapshot(), value.matchTrace(), value.configGapSnapshot(),
                    value.items() == null ? null : value.items().stream().map(Item::from).toList());
        }
    }

    public record Item(Long itemId, String stableItemKey, String itemTypeCode,
            String itemName, String itemDescription, String interfaceFormatCode,
            String interfaceSchemaSnapshot, String workModeCode, boolean required,
            String sourceCode, boolean applicable, Integer sortOrder,
            CurrentResult currentResult) {
        public static Item from(CutoverChecklistView.Item value) {
            if (value == null) return null;
            return new Item(value.itemId(), value.stableItemKey(), value.itemTypeCode(),
                    value.itemName(), value.itemDescription(), value.interfaceFormatCode(),
                    value.interfaceSchemaSnapshot(), value.workModeCode(), value.required(),
                    value.sourceCode(), value.applicable(), value.sortOrder(),
                    CurrentResult.from(value.currentResult()));
        }
    }

    @lombok.Builder
    public record CurrentResult(Integer resultVersion, String resultSourceCode, String answerSnapshot,
            String factDescription, String manualEvidenceFileReference, Long collectionTaskId,
            Long collectionResultReferenceId, Long collectionResultVersion, String loadFailureCode, String originalFailureCode) {
        public static CurrentResult from(CutoverChecklistView.CurrentResult value) {
            if (value == null) return null;
            return CurrentResult.builder().resultVersion(value.resultVersion()).resultSourceCode(value.resultSourceCode())
                    .answerSnapshot(value.answerSnapshot()).factDescription(value.factDescription())
                    .manualEvidenceFileReference(value.manualEvidenceFileReference()).collectionTaskId(value.collectionTaskId())
                    .collectionResultReferenceId(value.collectionResultReferenceId()).collectionResultVersion(value.collectionResultVersion())
                    .loadFailureCode(value.loadFailureCode()).originalFailureCode(value.originalFailureCode()).build();
        }
    }

    public record MatchingOptions(Long configurationRevisionId, Integer configurationRevisionNo, List<Option> cutoverTypes,
            List<Option> networkModes, List<Option> deviceTypes, cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput matchingInput) {
        public static MatchingOptions from(CutoverChecklistMatchingOptions value) {
            if (value == null) return null;
            return new MatchingOptions(value.configurationRevisionId(), value.configurationRevisionNo(), value.cutoverTypes() == null ? null : value.cutoverTypes().stream().map(Option::from).toList(),
                    value.networkModes() == null ? null : value.networkModes().stream().map(Option::from).toList(), value.deviceTypes() == null ? null : value.deviceTypes().stream().map(Option::from).toList(), value.matchingInput());
        }
    }

    public record Option(String value, String label) {
        public static Option from(CutoverIntakeConfigurationPolicy.Option value) {
            if (value == null) return null;
            return new Option(value.value(), value.label());
        }
    }

    public record ChecklistCommandResult(Long taskId, Long checklistId, Long checklistVersion,
            Long checklistFactVersion, String checklistStatus, String taskStage,
            Long taskVersion, boolean replayed, NavigationDecision navigationDecision) {
        public static ChecklistCommandResult from(cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult value) {
            if (value == null) return null;
            return new ChecklistCommandResult(value.taskId(), value.checklistId(), value.checklistVersion(),
                    value.checklistFactVersion(), value.checklistStatus(), value.taskStage(),
                    value.taskVersion(), value.replayed(), NavigationDecision.from(value.navigationDecision()));
        }
    }

    public record ChecklistItemCommandResult(Long checklistId, Long checklistVersion, Long checklistItemId,
            String stableItemKey, Integer resultVersion) {
        public static ChecklistItemCommandResult from(cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult value) {
            if (value == null) return null;
            return new ChecklistItemCommandResult(value.checklistId(), value.checklistVersion(), value.checklistItemId(),
                    value.stableItemKey(), value.resultVersion());
        }
    }

    public record CollectionRequestCommandResult(Long taskId, Long taskVersion, Long checklistId,
            Long checklistBusinessVersion, Long checklistVersion, Long checklistItemId,
            Integer itemVersion, String stableItemKey, Integer resultVersion,
            Long collectionTaskId, Long collectionResultReferenceId, Long collectionResultVersion,
            String technicalStatus, String failureCode, boolean resultLinked,
            boolean replayed) {
        public static CollectionRequestCommandResult from(cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CollectionRequestCommandResult value) {
            if (value == null) return null;
            return new CollectionRequestCommandResult(value.taskId(), value.taskVersion(), value.checklistId(),
                    value.checklistBusinessVersion(), value.checklistVersion(), value.checklistItemId(),
                    value.itemVersion(), value.stableItemKey(), value.resultVersion(),
                    value.collectionTaskId(), value.collectionResultReferenceId(), value.collectionResultVersion(),
                    value.technicalStatus(), value.failureCode(), value.resultLinked(),
                    value.replayed());
        }
    }

    public record NavigationDecision(String ruleKey, Long configurationRevisionId, String target) {
        public static NavigationDecision from(cn.iocoder.yudao.module.pms.cutover.service.checklist.result.NavigationDecision value) {
            if (value == null) return null;
            return new NavigationDecision(value.ruleKey(), value.configurationRevisionId(), value.target());
        }
    }

}
