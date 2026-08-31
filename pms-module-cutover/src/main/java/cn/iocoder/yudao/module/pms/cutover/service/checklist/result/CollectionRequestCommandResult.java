package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record CollectionRequestCommandResult(Long checklistId, Integer checklistVersion,
                                             Long checklistItemId, String stableItemKey,
                                             Integer resultVersion, Long collectionTaskId,
                                             String technicalStatus, String failureCode,
                                             boolean replayed) {

    public CollectionRequestCommandResult replayedCopy() {
        return new CollectionRequestCommandResult(checklistId, checklistVersion, checklistItemId, stableItemKey,
                resultVersion, collectionTaskId, technicalStatus, failureCode, true);
    }
}
