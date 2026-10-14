package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.*;

/** 只处理字段树明确声明的自定义值；文件与嵌套表格关系分别进入专用表。 */
@Service
@RequiredArgsConstructor
public class CutoverCustomResultStore {
    private final CutoverResultCustomValueMapper values;
    private final CutoverResultCustomRowMapper rows;
    private final CutoverChecklistResultFileMapper files;
    private final CutoverChecklistMapper checklists;
    private final CutoverTaskMapper tasks;
    private final CutoverChecklistFilePort filePort;

    public record Write(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result,
                        CutoverFieldSchemaCodec.Definition definition, JsonNode value) { }
    public record Read(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result,
                       CutoverFieldSchemaCodec.Definition definition, Object builtinValue) { }
    public record Manual(CutoverChecklistItemResultDO result, Long fieldDefinitionId,
                         CutoverChecklistFilePort.FileFact fact) { }
    private record Node(Write write, CutoverChecklistFieldDefinitionDO field, JsonNode value, Long parentRowId) { }
    private record FileWrite(CutoverChecklistItemResultDO result, Long fieldId, Long rowId, int number,
                             String role, CutoverChecklistFilePort.FileFact fact) { }
    private record ReadData(Read query, List<CutoverResultCustomValueDO> values,
                            List<CutoverResultCustomRowDO> rows, List<CutoverChecklistResultFileDO> files) { }
    private record ReadNode(ReadData data, CutoverChecklistFieldDefinitionDO field, Object base, Long parentRowId) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Write command) {
        writeNode(new Node(command, CutoverChecklistResultStore.root(command.definition()), command.value(), null), new int[]{0});
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertManual(Manual command) {
        insertFile(new FileWrite(command.result(), command.fieldDefinitionId(), null, 0, "MANUAL_EVIDENCE", command.fact()));
    }

    private void writeNode(Node node, int[] fileNumber) {
        if (node.value() == null || node.value().isNull()) return;
        var children = children(node.write().definition(), node.field());
        if (isFile(node.field(), node.write().item())) {
            insertFile(new FileWrite(node.write().result(), node.field().getId(), node.parentRowId(), fileNumber[0]++,
                    "RESULT_FILE", requireFile(node)));
            return;
        }
        if (node.field().getBuiltinFieldCode() != null) {
            // 固定表格已按具名列及逐行扩展保存，不再制作通用结果行。
            if (isTable(node.field(), children)) return;
            if (node.value().isObject()) {
                rejectUnknown(node.value(), children);
                for (var child : children) {
                    if (child.getBuiltinFieldCode() == null) writeNode(new Node(node.write(), child,
                            node.value().get(child.getFieldCode()), node.parentRowId()), fileNumber);
                }
            }
            return;
        }
        if (isTable(node.field(), children)) {
            JsonNode input = node.value().isArray() ? node.value() : node.value().get("rows");
            if (input == null || !input.isArray()) throw new IllegalArgumentException("自定义表格须为行数组");
            for (int i = 0; i < input.size(); i++) {
                JsonNode value = input.get(i);
                if (!value.isObject()) throw new IllegalArgumentException("自定义表格行必须是对象");
                var root = node.write().result();
                var row = CutoverResultCustomRowDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                        .tableFieldDefinitionId(node.field().getId()).parentCustomRowId(node.parentRowId()).rowNo(i)
                        .extensionValues(JsonUtils.toJsonString(inline(new Node(node.write(), node.field(), value, null))))
                        .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
                rows.insert(row);
                writeRelations(new Node(node.write(), node.field(), value, row.getId()), fileNumber);
            }
        } else {
            var root = node.write().result();
            Map<String, Object> envelope = new LinkedHashMap<>(); envelope.put("value", inline(node));
            values.insert(CutoverResultCustomValueDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                    .fieldDefinitionId(node.field().getId()).extensionValues(JsonUtils.toJsonString(envelope))
                    .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build());
            writeRelations(node, fileNumber);
        }
    }

    private void writeRelations(Node node, int[] fileNumber) {
        for (var child : children(node.write().definition(), node.field())) {
            var nested = new Node(node.write(), child, node.value().get(child.getFieldCode()), node.parentRowId());
            if (nested.value() == null || nested.value().isNull()) continue;
            var descendants = children(node.write().definition(), child);
            if (isFile(child, node.write().item()) || isTable(child, descendants)) writeNode(nested, fileNumber);
            else if (!descendants.isEmpty()) writeRelations(nested, fileNumber);
        }
    }

    private Object inline(Node node) {
        var fields = children(node.write().definition(), node.field());
        if (fields.isEmpty()) return JsonUtils.getObjectMapper().convertValue(node.value(), Object.class);
        if (!node.value().isObject()) throw new IllegalArgumentException("自定义结构须为对象");
        rejectUnknown(node.value(), fields);
        Map<String, Object> result = new LinkedHashMap<>();
        for (var field : fields) {
            if (field.getBuiltinFieldCode() != null || field.getExtensionFieldKey() == null) {
                throw new IllegalArgumentException("固定业务字段不能进入自定义值");
            }
            if (!node.value().has(field.getFieldCode())) continue;
            var children = children(node.write().definition(), field);
            if (isFile(field, node.write().item()) || isTable(field, children)) continue;
            JsonNode value = node.value().get(field.getFieldCode());
            result.put(field.getExtensionFieldKey(), value.isNull() ? null : inline(new Node(node.write(), field, value, node.parentRowId())));
        }
        return result;
    }

    private CutoverChecklistFilePort.FileFact requireFile(Node node) {
        var item = node.write().item();
        var checklist = checklists.selectById(item.getChecklistId());
        if (checklist == null || !Objects.equals(checklist.getTenantId(), item.getTenantId())) throw new IllegalArgumentException("文件所属清单不存在");
        var task = tasks.selectById(checklist.getCutoverTaskId());
        if (task == null || !Objects.equals(task.getTenantId(), item.getTenantId())) throw new IllegalArgumentException("文件所属任务不存在");
        var handle = JsonUtils.parseObject(node.value().toString(), CutoverChecklistFilePort.FileHandle.class);
        if (handle == null || handle.scopeVersion() == null) throw new IllegalArgumentException("文件引用事实缺失");
        var fact = filePort.lockAndRevalidate(CutoverChecklistFilePort.FileQuery.builder().tenantId(item.getTenantId())
                .actorId(node.write().result().getCreatedBy()).projectId(task.getProjectId()).checklistItemId(item.getId())
                .expectedScopeVersion(handle.scopeVersion()).handle(handle).build());
        if (fact == null || !Objects.equals(handle.artifactId(), fact.artifactId())
                || !Objects.equals(handle.versionNo(), fact.versionNo()) || !Objects.equals(handle.referenceKey(), fact.referenceKey())
                || !Objects.equals(handle.fileFactVersion(), fact.fileFactVersion()) || !Objects.equals(handle.scopeVersion(), fact.scopeVersion())) {
            throw new CutoverChecklistException(CutoverChecklistException.Code.FILE_FACT_INVALID, "文件事实已变化");
        }
        return fact;
    }

    private void insertFile(FileWrite command) {
        var root = command.result(); var fact = command.fact(); var versions = fact.fileFactVersion();
        files.insert(CutoverChecklistResultFileDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .fieldDefinitionId(command.fieldId()).customResultRowId(command.rowId()).fileNo(command.number()).fileRoleCode(command.role())
                .fileArtifactId(fact.artifactId()).fileVersionNo(fact.versionNo()).fileReferenceKey(fact.referenceKey())
                .fileArtifactFactVersion(versions.artifactVersion().longValue()).fileReferenceFactVersion(versions.referenceVersion().longValue())
                .fileAvailabilityFactVersion(versions.availabilityVersion().longValue()).fileScopeVersion(fact.scopeVersion()).fileSha256(fact.sha256())
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build());
    }

    public Object merge(Read query) {
        var root = query.result(); var ids = Set.of(root.getId());
        var data = new ReadData(query,
                values.selectListByParents(CutoverResultCustomValueListQuery.builder().tenantId(root.getTenantId()).checklistResultIds(ids).build()),
                rows.selectListByParents(CutoverResultCustomRowListQuery.builder().tenantId(root.getTenantId()).checklistResultIds(ids).build()),
                files.selectListByParents(CutoverChecklistResultFileListQuery.builder().tenantId(root.getTenantId()).checklistResultIds(ids).build()));
        return readNode(new ReadNode(data, CutoverChecklistResultStore.root(query.definition()), query.builtinValue(), null));
    }

    public CutoverChecklistFilePort.FileFact manualFile(CutoverChecklistItemResultDO result) {
        return files.selectListByParents(CutoverChecklistResultFileListQuery.builder().tenantId(result.getTenantId())
                .checklistResultIds(Set.of(result.getId())).build()).stream().filter(row -> "MANUAL_EVIDENCE".equals(row.getFileRoleCode()))
                .findFirst().map(CutoverCustomResultStore::fileFact).orElse(null);
    }

    private Object readNode(ReadNode node) {
        var definition = node.data().query().definition(); var children = children(definition, node.field());
        if (isFile(node.field(), node.data().query().item()) || node.data().files().stream().anyMatch(row ->
                Objects.equals(row.getFieldDefinitionId(), node.field().getId())
                        && Objects.equals(row.getCustomResultRowId(), node.parentRowId()) && "RESULT_FILE".equals(row.getFileRoleCode()))) {
            return node.data().files().stream().filter(row -> Objects.equals(row.getFieldDefinitionId(), node.field().getId())
                    && Objects.equals(row.getCustomResultRowId(), node.parentRowId())).findFirst()
                    .map(CutoverCustomResultStore::fileFact).orElse(null);
        }
        if (node.field().getBuiltinFieldCode() != null && isTable(node.field(), children)) return node.base();
        if (node.field().getBuiltinFieldCode() == null && isTable(node.field(), children)) {
            return node.data().rows().stream().filter(row -> node.field().getId().equals(row.getTableFieldDefinitionId())
                    && Objects.equals(row.getParentCustomRowId(), node.parentRowId())).sorted(Comparator.comparing(CutoverResultCustomRowDO::getRowNo))
                    .map(row -> mergeRelations(new ReadNode(node.data(), node.field(),
                            JsonUtils.parseObject(row.getExtensionValues(), Map.class), row.getId()))).toList();
        }
        Object base = node.base();
        if (node.field().getBuiltinFieldCode() == null) {
            base = node.data().values().stream().filter(row -> node.field().getId().equals(row.getFieldDefinitionId())).findFirst()
                    .map(row -> JsonUtils.parseObject(row.getExtensionValues(), Map.class).get("value")).orElse(base);
        }
        return children.isEmpty() ? base : mergeRelations(new ReadNode(node.data(), node.field(), base, node.parentRowId()));
    }

    @SuppressWarnings("unchecked")
    private Object mergeRelations(ReadNode node) {
        Map<String, Object> result = node.base() instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        for (var child : children(node.data().query().definition(), node.field())) {
            if (child.getBuiltinFieldCode() != null) continue;
            Object value = readNode(new ReadNode(node.data(), child, result.get(child.getFieldCode()), node.parentRowId()));
            if (value != null) result.put(child.getFieldCode(), value);
        }
        return result;
    }

    private static CutoverChecklistFilePort.FileFact fileFact(CutoverChecklistResultFileDO row) {
        return CutoverChecklistFilePort.FileFact.builder().artifactId(row.getFileArtifactId()).versionNo(row.getFileVersionNo())
                .referenceKey(row.getFileReferenceKey()).scopeVersion(row.getFileScopeVersion()).sha256(row.getFileSha256())
                .fileFactVersion(new CutoverChecklistFilePort.FileFactVersion(Math.toIntExact(row.getFileArtifactFactVersion()),
                        Math.toIntExact(row.getFileReferenceFactVersion()), Math.toIntExact(row.getFileAvailabilityFactVersion()))).build();
    }

    private static List<CutoverChecklistFieldDefinitionDO> children(CutoverFieldSchemaCodec.Definition definition,
                                                                    CutoverChecklistFieldDefinitionDO parent) {
        return definition.fields().stream().filter(field -> Objects.equals(field.getParentFieldId(), parent.getId()))
                .sorted(Comparator.comparing(CutoverChecklistFieldDefinitionDO::getSortOrder)).toList();
    }
    private static boolean isFile(CutoverChecklistFieldDefinitionDO field, CutoverChecklistItemDO item) {
        return "FILE".equals(field.getDataTypeCode()) || (field.getParentFieldId() == null && "FILE".equals(item.getInterfaceFormatCode()));
    }
    private static boolean isTable(CutoverChecklistFieldDefinitionDO field, List<CutoverChecklistFieldDefinitionDO> children) {
        return "TABLE".equals(field.getDataTypeCode()) || children.stream().anyMatch(child -> "COLUMN".equals(child.getNodeKindCode()));
    }
    private static void rejectUnknown(JsonNode value, List<CutoverChecklistFieldDefinitionDO> fields) {
        Set<String> allowed = new HashSet<>(); fields.forEach(field -> allowed.add(field.getFieldCode()));
        for (String key : value.propertyNames()) if (!allowed.contains(key)) throw new IllegalArgumentException("答案包含未定义字段: " + key);
    }
}
