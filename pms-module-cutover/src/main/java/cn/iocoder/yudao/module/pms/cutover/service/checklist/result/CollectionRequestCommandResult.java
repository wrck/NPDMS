package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record CollectionRequestCommandResult(Long taskId, Integer taskVersion,
                                             Long checklistId, Integer checklistBusinessVersion,
                                             Integer checklistVersion, Long checklistItemId,
                                             Integer itemVersion, String stableItemKey,
                                             Integer resultVersion, Long collectionTaskId,
                                             Long collectionResultReferenceId,
                                             Long collectionResultVersion,
                                             String technicalStatus, String failureCode,
                                             boolean resultLinked, boolean replayed) {

    public CollectionRequestCommandResult replayedCopy() {
        return new CollectionRequestCommandResult(taskId, taskVersion, checklistId, checklistBusinessVersion,
                checklistVersion, checklistItemId, itemVersion, stableItemKey, resultVersion,
                collectionTaskId, collectionResultReferenceId, collectionResultVersion,
                technicalStatus, failureCode, resultLinked, true);
    }
}
