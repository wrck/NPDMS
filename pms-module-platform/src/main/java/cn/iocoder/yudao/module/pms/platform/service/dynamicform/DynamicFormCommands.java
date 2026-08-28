package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import tools.jackson.databind.JsonNode;

/** 共享动态表单应用命令。 */
public final class DynamicFormCommands {

    private DynamicFormCommands() {
    }

    public record Actor(Long tenantId, Long userId) {
    }

    /** 保留PATCH字段的“未出现”与“显式null”差异。 */
    public record FieldPatch<T>(boolean present, T value) {
        public static <T> FieldPatch<T> absent() {
            return new FieldPatch<>(false, null);
        }

        public static <T> FieldPatch<T> present(T value) {
            return new FieldPatch<>(true, value);
        }
    }

    public record CreateTemplate(Actor actor, String idempotencyKey, String templateCode,
                                 String templateName, String categoryCode, String description) {
    }

    public record PatchTemplate(Actor actor, Long templateId, Integer expectedVersion,
                                FieldPatch<String> templateName, FieldPatch<String> categoryCode,
                                FieldPatch<String> description, String correlationId) {
    }

    public record CreateRevision(Actor actor, Long templateId, Integer expectedTemplateVersion,
                                 String idempotencyKey) {
    }

    public record PatchRevision(Actor actor, Long revisionId, Integer expectedVersion,
                                JsonNode formConfJson, JsonNode formRulesJson,
                                String engineCode, String designerVersion, String rendererVersion,
                                String correlationId) {
    }

    public record PublishRevision(Actor actor, Long revisionId, Integer expectedVersion,
                                  String idempotencyKey) {
    }

    public record SetAvailability(Actor actor, Long templateId, Integer expectedVersion,
                                  String targetAvailability, String idempotencyKey) {
    }

    public record CreateInstance(Actor actor, Long templateRevisionId, Integer expectedTemplateVersion,
                                 String instanceName, String idempotencyKey) {
    }

    public record PatchInstance(Actor actor, Long instanceId, Integer expectedVersion,
                                JsonNode values, String correlationId) {
    }
}
