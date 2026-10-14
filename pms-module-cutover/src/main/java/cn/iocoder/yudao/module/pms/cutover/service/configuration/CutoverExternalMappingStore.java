package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistExternalMappingDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistExternalMappingMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistExternalMappingListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 外部接口标识在采集项修订，输入/输出的明确映射逐行保存。 */
@Service
@RequiredArgsConstructor
public class CutoverExternalMappingStore {
    private final CutoverChecklistExternalMappingMapper mappings;

    public record Write(CutoverChecklistItemDefinitionRevisionDO item, Map<String, Object> configuration) { }

    public void setInterface(Write command) {
        Map<String, Object> source = command.configuration();
        if (source == null) return;
        if (!Set.of("interfaceCode", "manualFallback", "parameterMapping", "returnMapping").containsAll(source.keySet())) {
            throw new IllegalArgumentException("外部来源配置包含未知字段");
        }
        if (source.get("interfaceCode") != null && !(source.get("interfaceCode") instanceof String)) {
            throw new IllegalArgumentException("外部接口标识必须是文本");
        }
        if (source.get("manualFallback") != null && !(source.get("manualFallback") instanceof Boolean)) {
            throw new IllegalArgumentException("人工降级标识必须是布尔值");
        }
        command.item().setExternalInterfaceCode((String) source.get("interfaceCode"));
        command.item().setExternalManualFallbackFlag((Boolean) source.get("manualFallback"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Write command) {
        if (command.configuration() == null) return;
        for (String direction : List.of("PARAM", "RETURN")) {
            Object raw = command.configuration().get("PARAM".equals(direction) ? "parameterMapping" : "returnMapping");
            if (raw == null) continue;
            if (!(raw instanceof Map<?, ?> values)) throw new IllegalArgumentException("外部字段映射必须是对象");
            int index = 0;
            for (var entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String target) || target.isBlank()
                        || !(entry.getValue() instanceof Map<?, ?> value)
                        || !value.keySet().equals(Set.of("sourcePath", "valueType", "required"))
                        || !(value.get("sourcePath") instanceof String path) || path.isBlank()
                        || !(value.get("valueType") instanceof String type) || type.isBlank()
                        || !(value.get("required") instanceof Boolean required)) {
                    throw new IllegalArgumentException("每个外部映射必须明确sourcePath、valueType、required");
                }
                var row = CutoverChecklistExternalMappingDO.builder().itemDefinitionRevisionId(command.item().getId())
                        .mappingDirectionCode(direction).mappingNo(index++).sourcePath(path).targetKey(target)
                        .valueTypeCode(type).requiredFlag(required).build();
                row.setTenantId(TenantContextHolder.getRequiredTenantId()); row.setDeleted(false);
                mappings.insert(row);
            }
        }
    }

    public Map<Long, Map<String, Object>> views(List<CutoverChecklistItemDefinitionRevisionDO> items) {
        Set<Long> ids = items.stream().map(CutoverChecklistItemDefinitionRevisionDO::getId).collect(Collectors.toSet());
        var rows = mappings.selectListByParents(CutoverChecklistExternalMappingListQuery.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).itemDefinitionRevisionIds(ids).build());
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (var item : items) {
            var itemRows = rows.stream().filter(row -> item.getId().equals(row.getItemDefinitionRevisionId())).toList();
            if (item.getExternalInterfaceCode() == null && item.getExternalManualFallbackFlag() == null && itemRows.isEmpty()) continue;
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("interfaceCode", item.getExternalInterfaceCode());
            source.put("manualFallback", item.getExternalManualFallbackFlag());
            for (String direction : List.of("PARAM", "RETURN")) {
                Map<String, Object> values = new LinkedHashMap<>();
                itemRows.stream().filter(row -> direction.equals(row.getMappingDirectionCode())).forEach(row ->
                        values.put(row.getTargetKey(), Map.of("sourcePath", row.getSourcePath(),
                                "valueType", row.getValueTypeCode(), "required", row.getRequiredFlag())));
                source.put("PARAM".equals(direction) ? "parameterMapping" : "returnMapping", values);
            }
            result.put(item.getId(), source);
        }
        return result;
    }
}
