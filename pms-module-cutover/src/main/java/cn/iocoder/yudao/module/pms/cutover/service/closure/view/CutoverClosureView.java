package cn.iocoder.yudao.module.pms.cutover.service.closure.view;

import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;

import java.time.LocalDateTime;
import java.util.List;

public record CutoverClosureView(Long taskId, String taskStage, String taskStatus, Long taskVersion,
                                 Long closureId, Long closureVersion, String closureStatus,
                                 Long approvalInstanceId, Long approvalVersion,
                                 Long planRevisionId, Integer planRevisionNo, Long planVersion,
                                 ClosureContent content, List<CollectionEvidenceView> collectionEvidence,
                                 String resultRef, Long submittedBy, LocalDateTime submittedAt,
                                 LocalDateTime archivedAt, List<String> allowedActions) {

    public record CollectionEvidenceView(Long evidenceId, Long deviceId, String collectionStage,
                                         String evidenceType, String collectionTaskId,
                                         String callbackEventId, String resultRef, String resultVersion,
                                         String originalFailedCollectionTaskId, AttachmentInput manualFile,
                                         LocalDateTime occurredAt) {
    }
}
