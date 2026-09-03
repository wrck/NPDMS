package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.closure;

import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** F-CUT-006 ClosureView精确REST投影。 */
public record CutoverClosureResponse(Long taskId, String taskStage, String taskStatus, Integer taskVersion,
                                     Long closureId, Integer closureVersion, String closureStatus,
                                     Long approvalInstanceId, Integer approvalVersion,
                                     Long planRevisionId, Integer planRevisionNo, Integer planVersion,
                                     ClosureContent content, List<CollectionEvidenceResponse> collectionEvidence,
                                     String resultRef, Long submittedBy, Long submittedAt,
                                     Long archivedAt, List<String> allowedActions) {

    public static CutoverClosureResponse from(CutoverClosureView value) {
        return new CutoverClosureResponse(value.taskId(), value.taskStage(), value.taskStatus(), value.taskVersion(),
                value.closureId(), value.closureVersion(), value.closureStatus(), value.approvalInstanceId(),
                value.approvalVersion(), value.planRevisionId(), value.planRevisionNo(), value.planVersion(),
                value.content(), value.collectionEvidence().stream().map(CollectionEvidenceResponse::from).toList(),
                value.resultRef(), value.submittedBy(), wireDateTime(value.submittedAt()),
                wireDateTime(value.archivedAt()), value.allowedActions());
    }

    public record CollectionEvidenceResponse(Long evidenceId, Long deviceId, String collectionStage,
                                             String evidenceType, String collectionTaskId,
                                             String callbackEventId, String resultRef, String resultVersion,
                                             String originalFailedCollectionTaskId, AttachmentInput manualFile,
                                             Long occurredAt) {
        private static CollectionEvidenceResponse from(CutoverClosureView.CollectionEvidenceView value) {
            return new CollectionEvidenceResponse(value.evidenceId(), value.deviceId(), value.collectionStage(),
                    value.evidenceType(), value.collectionTaskId(), value.callbackEventId(), value.resultRef(),
                    value.resultVersion(), value.originalFailedCollectionTaskId(), value.manualFile(),
                    wireDateTime(value.occurredAt()));
        }
    }

    private static Long wireDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
