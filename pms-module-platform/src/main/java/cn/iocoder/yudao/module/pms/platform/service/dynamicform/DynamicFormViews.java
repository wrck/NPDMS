package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Controller无关的共享动态表单查询和命令结果。 */
public final class DynamicFormViews {

    private DynamicFormViews() {
    }

    public record Page<T>(List<T> list, long total) {
        public Page {
            list = list == null ? List.of() : List.copyOf(list);
        }
    }

    public record RevisionSummary(Long revisionId, Integer revisionNo, String status,
                                  Integer revisionVersion, Long sourceRevisionId,
                                  String engineCode, String designerVersion, String rendererVersion,
                                  Long publishedBy, LocalDateTime publishedAt) {
    }

    public record Template(Long templateId, String templateCode, String templateName,
                           String categoryCode, String description, String availability,
                           Integer templateVersion, Long currentPublishedRevisionId,
                           RevisionSummary currentDraft, RevisionSummary currentPublished,
                           Set<String> allowedActions, LocalDateTime createTime, LocalDateTime updateTime) {
        public Template {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record Revision(Long revisionId, Long templateId, Integer revisionNo, String status,
                           Long sourceRevisionId, JsonNode formConfJson, JsonNode formRulesJson,
                           String engineCode, String designerVersion, String rendererVersion,
                           Integer revisionVersion, Long publishedBy, LocalDateTime publishedAt,
                           Set<String> allowedActions) {
        public Revision {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record Selection(Long templateId, String templateCode, String templateName,
                            String categoryCode, String description, Long currentPublishedRevisionId,
                            Integer currentPublishedRevisionNo, String engineCode,
                            String designerVersion, String rendererVersion, Integer templateVersion,
                            Set<String> allowedActions) {
        public Selection {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record PublishResult(Long templateId, Integer templateVersion, String availability,
                                Revision revision, Set<String> allowedActions) {
        public PublishResult {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record FileFact(Long artifactId, Integer versionNo, String referenceKey,
                           FileFactVersion fileFactVersion, Long scopeVersion, String status) {
    }

    public record InstanceSummary(Long instanceId, String instanceCode, String instanceName,
                                  Long templateId, String templateCode, String templateName,
                                  Long templateRevisionId, Integer templateRevisionNo,
                                  Integer instanceVersion, Long createdBy, Set<String> allowedActions,
                                  LocalDateTime createTime, LocalDateTime updateTime) {
        public InstanceSummary {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record InstanceCreated(Long instanceId, String instanceCode, Long templateId,
                                  Long templateRevisionId, Integer templateRevisionNo,
                                  Integer instanceVersion, Set<String> allowedActions) {
        public InstanceCreated {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record Instance(Long instanceId, String instanceCode, String instanceName,
                           Long templateId, String templateCode, String templateName,
                           Long templateRevisionId, Integer templateRevisionNo,
                           String engineCode, String designerVersion, String rendererVersion,
                           JsonNode formConfJson, JsonNode formRulesJson, JsonNode values,
                           Map<String, List<FileFact>> controlledFiles, Integer instanceVersion,
                           Long createdBy, Set<String> allowedActions,
                           LocalDateTime createTime, LocalDateTime updateTime) {
        public Instance {
            controlledFiles = controlledFiles == null ? Map.of() : Map.copyOf(controlledFiles);
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }

    public record InstancePatchResult(Long instanceId, Integer instanceVersion,
                                      List<String> changedFieldKeys, Set<String> allowedActions) {
        public InstancePatchResult {
            changedFieldKeys = changedFieldKeys == null ? List.of() : List.copyOf(changedFieldKeys);
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }
}
