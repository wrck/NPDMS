package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record DynamicFormInstanceRespVO(
        Long instanceId, String instanceCode, String instanceName, Long templateId,
        String templateCode, String templateName, Long templateRevisionId, Integer templateRevisionNo,
        String engineCode, String designerVersion, String rendererVersion,
        JsonNode formConfJson, JsonNode formRulesJson, JsonNode values,
        Map<String, List<DynamicFormFileFactRespVO>> controlledFiles, Integer instanceVersion,
        Long createdBy, Set<String> allowedActions, LocalDateTime createTime, LocalDateTime updateTime) {

    public static DynamicFormInstanceRespVO of(DynamicFormViews.Instance value) {
        Map<String, List<DynamicFormFileFactRespVO>> files = value.controlledFiles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .map(fact -> new DynamicFormFileFactRespVO(fact.artifactId(), fact.versionNo(),
                                fact.referenceKey(), fact.fileFactVersion(),
                                fact.scopeVersion(), fact.status())).toList()));
        return new DynamicFormInstanceRespVO(value.instanceId(), value.instanceCode(), value.instanceName(),
                value.templateId(), value.templateCode(), value.templateName(), value.templateRevisionId(),
                value.templateRevisionNo(), value.engineCode(), value.designerVersion(), value.rendererVersion(),
                value.formConfJson(), value.formRulesJson(), value.values(), files, value.instanceVersion(),
                value.createdBy(), value.allowedActions(), value.createTime(), value.updateTime());
    }
}
