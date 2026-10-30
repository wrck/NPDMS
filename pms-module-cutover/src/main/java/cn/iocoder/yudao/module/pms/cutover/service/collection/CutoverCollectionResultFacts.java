package cn.iocoder.yudao.module.pms.cutover.service.collection;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.collection.CutoverCollectionLogDO;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionBusinessResultReceiver.Result;

/** 平台回执的逐列映射；旧result_snapshot仅作为迁移前原件保留。 */
public final class CutoverCollectionResultFacts {
    private CutoverCollectionResultFacts() {}
    public record Write(CutoverCollectionLogDO row, Result result) {}

    public static void apply(Write command) {
        var row = command.row(); var result = command.result();
        row.setEntry(result.entry()); row.setObjectId(result.objectId()); row.setExecutionId(result.executionId());
        row.setPlatformTaskId(result.platformTaskId()); row.setResultVersion(result.resultVersion());
        row.setFileVersionId(result.fileVersionId()); row.setDeviceId(result.deviceId());
        row.setSourceContextCode(result.sourceContext()); row.setSourceObjectTypeCode(result.sourceObjectType());
        row.setProjectId(result.projectId()); row.setActorUserId(result.actorId()); row.setProtocolCode(result.protocol());
        row.setExternalStatusCode(result.externalStatus()); row.setFailureCategoryCode(result.failureCategory());
        row.setCommandText(result.commandText()); row.setTemplateName(result.templateName());
    }

    public static Result read(CutoverCollectionLogDO row) {
        return new Result(row.getTenantId(), row.getEntry(), row.getObjectId(), row.getSourceContextCode(),
                row.getSourceObjectTypeCode(), row.getProjectId(), row.getDeviceId(), row.getExecutionId(),
                row.getActorUserId(), row.getPlatformTaskId(), row.getResultVersion(), row.getFileVersionId(),
                row.getProtocolCode(), row.getExternalStatusCode(), row.getFailureCategoryCode(), row.getCommandText(), row.getTemplateName());
    }
}
