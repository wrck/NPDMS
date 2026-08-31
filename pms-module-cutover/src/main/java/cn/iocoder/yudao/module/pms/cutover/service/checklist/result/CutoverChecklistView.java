package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

import java.util.List;

public record CutoverChecklistView(Long taskId, String taskStage, Integer taskVersion, Long projectScopeVersion,
                                   Long checklistId, Integer checklistVersion, Integer checklistFactVersion,
                                   String status, String inputSnapshotHash, String configRevisionSnapshot,
                                   String matchTrace, String configGapSnapshot, List<Item> items) {

    public record Item(Long itemId, String stableItemKey, String itemTypeCode, String itemName,
                       String itemDescription, String interfaceFormatCode, String interfaceSchemaSnapshot,
                       String workModeCode, boolean required, String sourceCode, boolean applicable,
                       Integer sortOrder, CurrentResult currentResult) {
    }

    public record CurrentResult(Integer resultVersion, String resultSourceCode, String answerSnapshot,
                                String factDescription, String manualEvidenceFileReference,
                                Long collectionTaskId, Long collectionResultReferenceId,
                                Long collectionResultVersion, String loadFailureCode) {
    }
}
