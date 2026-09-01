package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;

import java.util.List;

public record SaveCutoverClosureCommand(Long tenantId, Long actorId, Long taskId,
                                        Integer expectedTaskVersion, Integer expectedClosureVersion,
                                        ClosureContent content, String idempotencyKey, String correlationId) {

    public record ClosureContent(Boolean preCheckNormal, String preCheckDetail,
                                 Boolean executionNormal, String executionDetail,
                                 Boolean testNormal, String testDetail,
                                 Boolean rollbackOccurred, Boolean rollbackSuccessful,
                                 String rollbackReason, String legacyItems, String finalResult,
                                 List<AttachmentInput> attachments) {
        public ClosureContent {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record AttachmentInput(AttachmentPurpose purposeCode, Long artifactId, Integer versionNo,
                                  String referenceKey, FileFactVersion fileFactVersion,
                                  Long scopeVersion, String sha256) {
    }
}
