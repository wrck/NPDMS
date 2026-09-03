package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** F-CUT-004 七路由请求边界；只做wire/schema解析，不做业务状态判断。 */
public final class CutoverPlanRequestCodec {
    private static final long JS_SAFE_INTEGER_BOUNDARY = 9_007_199_254_740_991L;
    private static final Set<String> CREATE_ONLINE = Set.of("editMode");
    private static final Set<String> CREATE_UPLOAD = Set.of("editMode", "fileArtifactFact", "ownershipConfirmed");
    private static final Set<String> FILE = Set.of("artifactId", "versionNo", "referenceKey", "fileFactVersion", "scopeVersion", "sha256");
    private static final Set<String> FILE_VERSION = Set.of("artifactVersion", "referenceVersion", "availabilityVersion");
    private static final Set<String> PATCH = Set.of("personName", "phone", "arrivalTime");
    private static final Set<String> REVISE = Set.of("sourcePlanRevisionId", "reason");
    private static final Set<String> STANDARD = Set.of("editMode", "overview", "steps", "riskMitigations", "supportArrangements");
    private static final Set<String> SIMPLE = Set.of("editMode", "steps");
    private static final Set<String> UPLOAD = Set.of("editMode", "fileArtifactFact", "ownershipConfirmed");

    public CreateDraft createDraft(JsonNode body) {
        requireObject(body, "body");
        String mode = textField(body, "editMode", 32);
        if (List.of("ONLINE_TEMPLATE_STANDARD", "ONLINE_TEMPLATE_SIMPLE_D").contains(mode)) {
            exact(body, CREATE_ONLINE, "createDraft");
            return new CreateDraft(mode, null, null);
        }
        if (!"FULL_FILE_UPLOAD".equals(mode)) throw invalid("editMode");
        exact(body, CREATE_UPLOAD, "createDraft");
        if (!body.path("ownershipConfirmed").isBoolean() || !body.path("ownershipConfirmed").asBoolean()) {
            throw invalid("ownershipConfirmed");
        }
        return new CreateDraft(mode, file(body.get("fileArtifactFact")), true);
    }

    public JsonNode draftContent(JsonNode body) {
        requireObject(body, "body");
        String mode = textField(body, "editMode", 32);
        exact(body, switch (mode) {
            case "ONLINE_TEMPLATE_STANDARD" -> STANDARD;
            case "ONLINE_TEMPLATE_SIMPLE_D" -> SIMPLE;
            case "FULL_FILE_UPLOAD" -> UPLOAD;
            default -> throw invalid("editMode");
        }, "draftContent");
        if ("FULL_FILE_UPLOAD".equals(mode)) file(body.get("fileArtifactFact"));
        return body.deepCopy();
    }

    public PatchContact patchContact(JsonNode body) {
        exact(body, PATCH, "patchContact");
        return new PatchContact(textField(body, "personName", 128), textField(body, "phone", 64),
                dateTime(body.get("arrivalTime"), "arrivalTime"));
    }

    public Revise revise(JsonNode body) {
        exact(body, REVISE, "revise");
        String reason = textField(body, "reason", 32);
        if (!List.of("APPROVAL_REJECTED", "DUTY_CHANGED", "SOURCE_REPLACED").contains(reason)) throw invalid("reason");
        return new Revise(wireLong(body.get("sourcePlanRevisionId"), "sourcePlanRevisionId", true), reason);
    }

    public void empty(JsonNode body) {
        if (body != null && (!body.isObject() || !body.isEmpty())) throw invalid("body");
    }

    public int version(String value, String name) {
        String text = header(value, name);
        try { int result = Integer.parseInt(text); if (result < 0) throw invalidHeader(name); return result; }
        catch (NumberFormatException ex) { throw invalidHeader(name); }
    }

    public String header(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
            throw invalidHeader(name);
        }
        return value;
    }

    private static CutoverPlanFilePort.FileFact file(JsonNode node) {
        exact(node, FILE, "fileArtifactFact");
        JsonNode version = node.get("fileFactVersion"); exact(version, FILE_VERSION, "fileFactVersion");
        return new CutoverPlanFilePort.FileFact(wireLong(node.get("artifactId"), "artifactId", true),
                positiveInt(node.get("versionNo"), "versionNo"), textField(node, "referenceKey", 128),
                new CutoverPlanFilePort.FileFactVersion(nonNegativeInt(version.get("artifactVersion"), "artifactVersion"),
                        nonNegativeInt(version.get("referenceVersion"), "referenceVersion"),
                        nonNegativeInt(version.get("availabilityVersion"), "availabilityVersion")),
                wireLong(node.get("scopeVersion"), "scopeVersion", false), textField(node, "sha256", 64));
    }

    private static LocalDateTime dateTime(JsonNode node, String field) {
        long value = wireLong(node, field, true);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault());
    }
    private static int positiveInt(JsonNode node, String field) { int value = nonNegativeInt(node, field); if (value == 0) throw invalid(field); return value; }
    private static int nonNegativeInt(JsonNode node, String field) { if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < 0) throw invalid(field); return node.asInt(); }
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
        }
        catch (NumberFormatException ex) { throw invalid(field); }
    }
    private static String textField(JsonNode node, String field, int max) { return textValue(node == null ? null : node.get(field), field, max); }
    private static String textValue(JsonNode node, String field, int max) { if (node == null || !node.isTextual()) throw invalid(field); String value=node.asText(); if (value.isBlank() || !value.equals(value.trim()) || value.length()>max) throw invalid(field); return value; }
    private static void requireObject(JsonNode node, String field) { if (node == null || !node.isObject()) throw invalid(field); }
    private static void exact(JsonNode node, Set<String> keys, String field) { requireObject(node, field); Set<String> actual=new HashSet<>(node.propertyNames()); if (!actual.equals(keys)) throw invalid(field); }
    private static CutoverPlanRequestException invalid(String field) {
        return new CutoverPlanRequestException(CutoverPlanRequestException.Reason.REQUEST_SCHEMA_INVALID,
                "invalid " + field);
    }
    private static CutoverPlanRequestException invalidHeader(String field) {
        return new CutoverPlanRequestException(CutoverPlanRequestException.Reason.HEADER_REQUIRED_OR_INVALID,
                "invalid " + field);
    }

    public record CreateDraft(String editMode, CutoverPlanFilePort.FileFact fileFact, Boolean ownershipConfirmed) {}
    public record PatchContact(String personName, String phone, LocalDateTime arrivalTime) {}
    public record Revise(Long sourcePlanRevisionId, String reason) {}
}
