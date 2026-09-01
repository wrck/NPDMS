package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.Authentication;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.SavedCredential;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.TransientCredential;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** F-CUT-006五路由wire/schema解析；不承载业务状态判断。 */
public final class CutoverClosureRequestCodec {
    private static final long JS_SAFE_INTEGER_BOUNDARY = 9_007_199_254_740_991L;
    private static final Set<String> CONTENT = Set.of("preCheckNormal", "preCheckDetail", "executionNormal",
            "executionDetail", "testNormal", "testDetail", "rollbackOccurred", "rollbackSuccessful",
            "rollbackReason", "legacyItems", "finalResult", "attachments");
    private static final Set<String> FILE = Set.of("purposeCode", "artifactId", "versionNo", "referenceKey",
            "fileFactVersion", "scopeVersion", "sha256");
    private static final Set<String> FILE_VERSION = Set.of("artifactVersion", "referenceVersion", "availabilityVersion");
    private static final Set<String> SAVED = Set.of("authenticationMode", "deviceId", "collectionStage",
            "credentialId", "credentialVersion", "templateCode", "templateVersion");
    private static final Set<String> TRANSIENT = Set.of("authenticationMode", "deviceId", "collectionStage",
            "loginName", "transientSecret", "saveAsCredential", "templateCode", "templateVersion");
    private static final Set<String> MANUAL = Set.of("originalFailedCollectionTaskId", "deviceId",
            "collectionStage", "file");
    private static final Set<String> SUBMIT = Set.of("finalResult");

    public ClosureContent content(JsonNode body) {
        exact(body, CONTENT, "content");
        if (!body.get("finalResult").isNull()) throw invalid("finalResult");
        JsonNode attachmentsNode = body.get("attachments");
        if (attachmentsNode == null || !attachmentsNode.isArray()) throw invalid("attachments");
        List<AttachmentInput> attachments = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (JsonNode node : attachmentsNode) {
            AttachmentInput attachment = file(node, false);
            String identity = attachment.purposeCode() + "\u0000" + attachment.referenceKey();
            if (!identities.add(identity)) throw invalid("attachments");
            attachments.add(attachment);
        }
        attachments.sort(java.util.Comparator.comparing((AttachmentInput value) -> value.purposeCode().name())
                .thenComparing(AttachmentInput::referenceKey));
        return new ClosureContent(nullableBoolean(body.get("preCheckNormal"), "preCheckNormal"),
                nullableText(body.get("preCheckDetail"), "preCheckDetail", 4000),
                nullableBoolean(body.get("executionNormal"), "executionNormal"),
                nullableText(body.get("executionDetail"), "executionDetail", 4000),
                nullableBoolean(body.get("testNormal"), "testNormal"),
                nullableText(body.get("testDetail"), "testDetail", 4000),
                nullableBoolean(body.get("rollbackOccurred"), "rollbackOccurred"),
                nullableBoolean(body.get("rollbackSuccessful"), "rollbackSuccessful"),
                nullableText(body.get("rollbackReason"), "rollbackReason", 4000),
                nullableText(body.get("legacyItems"), "legacyItems", 4000), null, attachments);
    }

    public CollectionRequest collection(JsonNode body) {
        requireObject(body, "body");
        String mode = text(body.get("authenticationMode"), "authenticationMode", 32);
        Set<String> keys = switch (mode) {
            case "SAVED_CREDENTIAL" -> SAVED;
            case "TRANSIENT_CREDENTIAL" -> TRANSIENT;
            default -> throw invalid("authenticationMode");
        };
        exact(body, keys, "collection");
        Authentication authentication;
        if ("SAVED_CREDENTIAL".equals(mode)) {
            authentication = new SavedCredential(wireLong(body.get("credentialId"), "credentialId", true),
                    wireLong(body.get("credentialVersion"), "credentialVersion", false));
        } else {
            JsonNode save = body.get("saveAsCredential");
            if (save == null || !save.isBoolean()) throw invalid("saveAsCredential");
            authentication = new TransientCredential(text(body.get("loginName"), "loginName", 128),
                    secret(body.get("transientSecret")), save.asBoolean());
        }
        return new CollectionRequest(wireLong(body.get("deviceId"), "deviceId", true),
                enumValue(body.get("collectionStage"), CollectionStage.class, "collectionStage"), authentication,
                text(body.get("templateCode"), "templateCode", 64),
                wireLong(body.get("templateVersion"), "templateVersion", false));
    }

    public ManualRequest manual(JsonNode body) {
        exact(body, MANUAL, "manualResult");
        return new ManualRequest(text(body.get("originalFailedCollectionTaskId"),
                "originalFailedCollectionTaskId", 128), wireLong(body.get("deviceId"), "deviceId", true),
                enumValue(body.get("collectionStage"), CollectionStage.class, "collectionStage"),
                file(body.get("file"), true));
    }

    public String finalResult(JsonNode body) {
        exact(body, SUBMIT, "submit");
        String value = text(body.get("finalResult"), "finalResult", 16);
        if (!List.of("SUCCESS", "FAILED").contains(value)) throw invalid("finalResult");
        return value;
    }

    public Integer optionalVersion(String value, String name) {
        return value == null ? null : version(value, name);
    }

    public int version(String value, String name) {
        String normalized = header(value, name);
        try {
            int result = Integer.parseInt(normalized);
            if (result < 0) throw invalidHeader(name);
            return result;
        } catch (NumberFormatException ex) {
            throw invalidHeader(name);
        }
    }

    public String header(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
            throw invalidHeader(name);
        }
        return value;
    }

    private static AttachmentInput file(JsonNode node, boolean manual) {
        exact(node, FILE, "file");
        AttachmentPurpose purpose = enumValue(node.get("purposeCode"), AttachmentPurpose.class, "purposeCode");
        if (manual != (purpose == AttachmentPurpose.MANUAL_COLLECTION_RESULT)) throw invalid("purposeCode");
        JsonNode version = node.get("fileFactVersion");
        exact(version, FILE_VERSION, "fileFactVersion");
        String sha256 = text(node.get("sha256"), "sha256", 64);
        if (!sha256.matches("[0-9a-f]{64}")) throw invalid("sha256");
        return new AttachmentInput(purpose, wireLong(node.get("artifactId"), "artifactId", true),
                positiveInt(node.get("versionNo"), "versionNo"), text(node.get("referenceKey"), "referenceKey", 128),
                new FileFactVersion(nonNegativeInt(version.get("artifactVersion"), "artifactVersion"),
                        nonNegativeInt(version.get("referenceVersion"), "referenceVersion"),
                        nonNegativeInt(version.get("availabilityVersion"), "availabilityVersion")),
                wireLong(node.get("scopeVersion"), "scopeVersion", false), sha256);
    }

    private static Boolean nullableBoolean(JsonNode node, String field) {
        if (node == null) throw invalid(field);
        if (node.isNull()) return null;
        if (!node.isBoolean()) throw invalid(field);
        return node.asBoolean();
    }

    private static String nullableText(JsonNode node, String field, int max) {
        if (node == null) throw invalid(field);
        if (node.isNull()) return null;
        if (!node.isTextual()) throw invalid(field);
        String value = node.asText();
        if (!value.equals(value.trim()) || value.length() > max) throw invalid(field);
        return value;
    }

    private static String secret(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("transientSecret");
        return node.asText();
    }

    private static int positiveInt(JsonNode node, String field) {
        int value = nonNegativeInt(node, field);
        if (value == 0) throw invalid(field);
        return value;
    }

    private static int nonNegativeInt(JsonNode node, String field) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < 0) throw invalid(field);
        return node.asInt();
    }

    private static long wireLong(JsonNode node, String field, boolean positive) {
        if (node == null || !(node.isIntegralNumber() || node.isTextual())) throw invalid(field);
        try {
            long value;
            if (node.isTextual()) {
                String text = node.asText();
                if (!text.matches("0|[1-9][0-9]*")) throw invalid(field);
                value = Long.parseLong(text);
            } else {
                if (!node.canConvertToLong()) throw invalid(field);
                value = node.asLong();
                if (value <= -JS_SAFE_INTEGER_BOUNDARY || value >= JS_SAFE_INTEGER_BOUNDARY) throw invalid(field);
            }
            if (positive ? value <= 0 : value < 0) throw invalid(field);
            return value;
        } catch (NumberFormatException ex) {
            throw invalid(field);
        }
    }

    private static String text(JsonNode node, String field, int max) {
        if (node == null || !node.isTextual()) throw invalid(field);
        String value = node.asText();
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > max) throw invalid(field);
        return value;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, Class<E> type, String field) {
        try {
            return Enum.valueOf(type, text(node, field, 64));
        } catch (IllegalArgumentException ex) {
            throw invalid(field);
        }
    }

    private static void exact(JsonNode node, Set<String> keys, String field) {
        requireObject(node, field);
        Set<String> actual = new HashSet<>();
        actual.addAll(node.propertyNames());
        if (!actual.equals(keys)) throw invalid(field);
    }

    private static void requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) throw invalid(field);
    }

    private static CutoverClosureRequestException invalid(String field) {
        return new CutoverClosureRequestException("invalid cutover closure request field: " + field);
    }

    private static CutoverClosureRequestException invalidHeader(String name) {
        return new CutoverClosureRequestException("invalid cutover closure header: " + name);
    }

    public record CollectionRequest(Long deviceId, CollectionStage collectionStage, Authentication authentication,
                                    String templateCode, Long templateVersion) { }
    public record ManualRequest(String originalFailedCollectionTaskId, Long deviceId,
                                CollectionStage collectionStage, AttachmentInput file) { }
}
