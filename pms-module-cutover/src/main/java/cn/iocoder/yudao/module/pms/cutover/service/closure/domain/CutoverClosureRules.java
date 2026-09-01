package cn.iocoder.yudao.module.pms.cutover.service.closure.domain;

import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureOwnerFactException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;

/** F-CUT-006 闭环字段与预留端口的最小机器规则。 */
public final class CutoverClosureRules {

    private CutoverClosureRules() {
    }

    public enum AttachmentPurpose {
        POST_COLLECTION_CHECKLIST,
        IMPLEMENTATION_COMMITMENT,
        OTHER_EVIDENCE,
        MANUAL_COLLECTION_RESULT
    }

    public enum CollectionStage {
        PRE_CHECK,
        EXECUTION,
        TEST,
        ROLLBACK,
        POST_COLLECTION
    }

    public enum AuthenticationMode {
        SAVED_CREDENTIAL,
        TRANSIENT_CREDENTIAL
    }

    public static Long positive(Long value, String field) {
        require(value != null && value > 0, field);
        return value;
    }

    public static long nonNegative(Long value, String field) {
        require(value != null && value >= 0, field);
        return value;
    }

    public static int positive(Integer value, String field) {
        require(value != null && value > 0, field);
        return value;
    }

    public static String normalizedText(String value, int maxLength, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim())
                && value.length() <= maxLength, field);
        return value;
    }

    public static String transientSecret(String value) {
        require(value != null && !value.isBlank(), "transientSecret");
        return value;
    }

    public static String sha256(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"), "sha256");
        return value;
    }

    public static LocalDateTime occurredAt(LocalDateTime value) {
        require(value != null, "occurredAt");
        return value;
    }

    public static void validateDraftContent(ClosureContent content) {
        requireValue(content, "content");
        optionalText(content.preCheckDetail(), 4000, "preCheckDetail");
        optionalText(content.executionDetail(), 4000, "executionDetail");
        optionalText(content.testDetail(), 4000, "testDetail");
        optionalText(content.rollbackReason(), 4000, "rollbackReason");
        optionalText(content.legacyItems(), 4000, "legacyItems");
        require(content.finalResult() == null || List.of("SUCCESS", "FAILED").contains(content.finalResult()),
                "finalResult");
        require(content.preCheckNormal() == null || content.preCheckNormal()
                || hasText(content.preCheckDetail()), "preCheckDetail");
        require(content.executionNormal() == null || content.executionNormal()
                || hasText(content.executionDetail()), "executionDetail");
        require(content.testNormal() == null || content.testNormal()
                || hasText(content.testDetail()), "testDetail");
        if (content.rollbackOccurred() == null || !content.rollbackOccurred()) {
            require(content.rollbackSuccessful() == null && content.rollbackReason() == null, "rollbackUnion");
        } else {
            require(content.rollbackSuccessful() != null && hasText(content.rollbackReason()), "rollbackUnion");
        }
        Set<String> identities = new HashSet<>();
        for (AttachmentInput attachment : content.attachments()) {
            requireValue(attachment, "attachment");
            requireValue(attachment.purposeCode(), "purposeCode");
            require(attachment.purposeCode() != AttachmentPurpose.MANUAL_COLLECTION_RESULT,
                    "manualCollectionResultForbidden");
            positive(attachment.artifactId(), "artifactId");
            positive(attachment.versionNo(), "versionNo");
            normalizedText(attachment.referenceKey(), 128, "referenceKey");
            requireValue(attachment.fileFactVersion(), "fileFactVersion");
            nonNegative(attachment.scopeVersion(), "scopeVersion");
            sha256(attachment.sha256());
            require(identities.add(attachment.purposeCode() + "\u0000" + attachment.referenceKey()),
                    "attachmentIdentity");
        }
    }

    private static void optionalText(String value, int maxLength, String field) {
        require(value == null || value.equals(value.trim()) && value.length() <= maxLength, field);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static <T> T requireValue(T value, String field) {
        require(value != null, field);
        return value;
    }

    public static void require(boolean condition, String field) {
        if (!condition) {
            throw new CutoverClosureOwnerFactException(
                    CutoverClosureOwnerFactException.Code.INVALID_REQUEST,
                    "invalid cutover closure contract field: " + field);
        }
    }
}
