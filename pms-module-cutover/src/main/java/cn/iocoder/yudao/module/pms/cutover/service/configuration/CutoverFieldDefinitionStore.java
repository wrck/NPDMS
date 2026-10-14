package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistFieldDefinitionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistFieldDefinitionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistFieldOptionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistFieldDefinitionListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistFieldOptionListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 字段树属于原采集项修订，根对象的保存/提交事务决定其写入时机。 */
@Service
@RequiredArgsConstructor
public class CutoverFieldDefinitionStore {
    private final CutoverChecklistFieldDefinitionMapper fields;
    private final CutoverChecklistFieldOptionMapper options;

    public CutoverFieldSchemaCodec.Definition forResult(CutoverChecklistItemResultDO result) {
        if (result.getFieldDefinitionId() == null) throw new IllegalStateException("结果的精确字段定义引用缺失");
        var root = fields.selectById(result.getFieldDefinitionId());
        if (root == null || root.getParentFieldId() != null || !Objects.equals(root.getTenantId(), result.getTenantId())
                || !Objects.equals(result.getTenantId(), TenantContextHolder.getRequiredTenantId()) || TenantContextHolder.isIgnore()) {
            throw new IllegalArgumentException("结果字段定义不属于当前租户或不是根定义");
        }
        if (root.getCustomChecklistItemId() != null && !root.getCustomChecklistItemId().equals(result.getChecklistItemId())) {
            throw new IllegalArgumentException("自定义结果字段定义不属于本采集项");
        }
        return root.getItemDefinitionRevisionId() == null ? customDefinition(root.getCustomChecklistItemId())
                : definitions(Set.of(root.getItemDefinitionRevisionId())).get(root.getItemDefinitionRevisionId());
    }

    public CutoverFieldSchemaCodec.Definition forItem(CutoverChecklistItemDO item) {
        if (!Objects.equals(item.getTenantId(), TenantContextHolder.getRequiredTenantId()) || TenantContextHolder.isIgnore()) {
            throw new IllegalArgumentException("采集项租户与当前上下文不一致");
        }
        return item.getItemDefinitionId() == null ? customDefinition(item.getId())
                : definitions(Set.of(item.getItemDefinitionId())).get(item.getItemDefinitionId());
    }

    /** 给 HTTP 校验和展示组装 schema，来源只有精确修订的字段树。 */
    public CutoverChecklistItemDO withSchema(CutoverChecklistItemDO item) {
        item.setInterfaceSchemaSnapshot(JsonUtils.toJsonString(CutoverFieldSchemaCodec.decode(forItem(item))));
        return item;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(CutoverFieldSchemaCodec.Write write) {
        if (!Objects.equals(write.tenantId(), TenantContextHolder.getRequiredTenantId()) || TenantContextHolder.isIgnore()) {
            throw new IllegalArgumentException("字段定义租户与当前上下文不一致");
        }
        CutoverFieldSchemaCodec.Definition definition = CutoverFieldSchemaCodec.encode(write);
        definition.fields().forEach(field -> { field.setDeleted(false); fields.insert(field); });
        definition.options().forEach(option -> { option.setDeleted(false); options.insert(option); });
    }

    public Map<Long, CutoverFieldSchemaCodec.Definition> definitions(Set<Long> itemDefinitionRevisionIds) {
        if (itemDefinitionRevisionIds.isEmpty()) return Map.of();
        List<CutoverChecklistFieldDefinitionDO> rows = fields.selectListByParents(
                CutoverChecklistFieldDefinitionListQuery.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                        .itemDefinitionRevisionIds(itemDefinitionRevisionIds).build());
        var choices = options.selectListByParents(CutoverChecklistFieldOptionListQuery.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .fieldDefinitionIds(rows.stream().map(CutoverChecklistFieldDefinitionDO::getId).collect(Collectors.toSet())).build());
        Map<Long, CutoverFieldSchemaCodec.Definition> result = new LinkedHashMap<>();
        for (Long id : itemDefinitionRevisionIds) {
            var tree = rows.stream().filter(row -> id.equals(row.getItemDefinitionRevisionId())).toList();
            Set<Long> fieldIds = tree.stream().map(CutoverChecklistFieldDefinitionDO::getId).collect(Collectors.toSet());
            result.put(id, new CutoverFieldSchemaCodec.Definition(tree,
                    choices.stream().filter(row -> fieldIds.contains(row.getFieldDefinitionId())).toList()));
        }
        return result;
    }

    public CutoverFieldSchemaCodec.Definition customDefinition(Long checklistItemId) {
        var rows = fields.selectListByParents(CutoverChecklistFieldDefinitionListQuery.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).customChecklistItemIds(Set.of(checklistItemId)).build());
        var choices = options.selectListByParents(CutoverChecklistFieldOptionListQuery.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .fieldDefinitionIds(rows.stream().map(CutoverChecklistFieldDefinitionDO::getId).collect(Collectors.toSet())).build());
        return new CutoverFieldSchemaCodec.Definition(rows, choices);
    }
}
