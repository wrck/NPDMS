package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.ReviseCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 历史清单不解锁；复制填报与受 PLT 授权保护的附件，自动查询/采集在新修订重新确认。 */
@Service
@RequiredArgsConstructor
public class CutoverChecklistRevisionService {
    private final CutoverChecklistMapper checklists;
    private final CutoverChecklistItemMapper items;
    private final CutoverChecklistItemResultMapper results;
    private final CutoverChecklistMetadataStore metadata;
    private final CutoverFieldDefinitionStore fields;
    private final CutoverChecklistResultStore answers;
    private final CutoverCustomResultStore custom;
    @jakarta.annotation.Resource @Lazy
    private FileArtifactApi files;

    public record Revision(ReviseCutoverTaskCommand command, CutoverTaskDO task, CutoverChecklistDO source, LocalDateTime time) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public CutoverChecklistDO copyDraft(Revision revision) {
        var command = revision.command(); var source = revision.source(); var task = revision.task();
        require(source != null && Objects.equals(source.getTenantId(), task.getTenantId())
                && Objects.equals(source.getCutoverTaskId(), task.getId()) && "P3".equals(task.getCurrentStage()), "修订清单来源不匹配");
        var draft = new CutoverChecklistDO(); BeanUtils.copyProperties(source, draft);
        draft.setId(IdUtil.getSnowflakeNextId()); draft.setStatusCode("DRAFT"); draft.setVersion(0L); draft.setCurrentMarker(null);
        var max = checklists.selectMaxChecklistVersion(new CutoverChecklistRowQuery(task.getTenantId(), task.getId(), null));
        draft.setChecklistVersion((max == null ? 0 : max) + 1);
        draft.setSubmittedAt(null); draft.setSubmittedBy(null); draft.setInvalidatedAt(null); draft.setInvalidatedReason(null);
        draft.setSourceTaskVersion(task.getVersion()); draft.setProjectScopeVersion(task.getProjectScopeVersion());
        draft.setInputSnapshotHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "sourceChecklistId", source.getId(), "sourceInputSnapshotHash", source.getInputSnapshotHash(), "taskVersion", task.getVersion()))));
        draft.setCreator(String.valueOf(command.actorId())); draft.setUpdater(String.valueOf(command.actorId()));
        draft.setCreateTime(revision.time()); draft.setUpdateTime(revision.time());
        require(checklists.insert(draft) == 1, "修订清单创建失败");
        metadata.copyToRevision(new CutoverChecklistMetadataStore.RevisionWrite(source, draft, command.actorId(), revision.time()));
        copyItems(new Copy(source, draft, command.actorId(), revision.time(), false));
        return draft;
    }

    public record Copy(CutoverChecklistDO source, CutoverChecklistDO target, Long actorId, LocalDateTime time, boolean matchedItemsExist) { }

    /** P2 重新确认后，只沿用仍命中相同精确定义的结果；自定义项继续属于同一任务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void copyItems(Copy command) {
        require(Objects.equals(command.source().getTenantId(), command.target().getTenantId())
                && Objects.equals(command.source().getCutoverTaskId(), command.target().getCutoverTaskId())
                && "DRAFT".equals(command.target().getStatusCode()), "清单来源必须属于同一任务");
        var priorItems = items.selectListByChecklist(new CutoverChecklistItemsQuery(command.source().getTenantId(), command.source().getId()));
        var previousResults = results.selectCurrentByChecklist(new CutoverChecklistResultsQuery(command.source().getTenantId(), command.source().getId()))
                .stream().collect(Collectors.toMap(CutoverChecklistItemResultDO::getChecklistItemId, value -> value));
        var existing = command.matchedItemsExist() ? items.selectListByChecklist(new CutoverChecklistItemsQuery(command.target().getTenantId(), command.target().getId()))
                .stream().collect(Collectors.toMap(CutoverChecklistItemDO::getStableItemKey, value -> value)) : Map.<String, CutoverChecklistItemDO>of();
        if (command.matchedItemsExist()) metadata.carryDeviceVersions(new CutoverChecklistMetadataStore.RevisionWrite(
                command.source(), command.target(), command.actorId(), command.time()));
        for (var previous : priorItems) {
            if (!Boolean.TRUE.equals(previous.getApplicableFlag())) continue;
            CutoverChecklistItemDO item = existing.get(previous.getStableItemKey());
            if (command.matchedItemsExist() && "SYSTEM_MATCHED".equals(previous.getSourceCode())) {
                if (item == null || !compatible(new CompatibleItems(previous, item))) continue;
            } else {
                item = copyItem(new ItemCopy(command, previous));
            }
            var selected = previousResults.get(previous.getId());
            if (selected == null || "EXTERNAL".equals(previous.getWorkModeCode()) || "COLLECTION".equals(selected.getResultSourceCode())) continue;
            if ("MANUAL".equals(selected.getResultSourceCode()) && !CutoverChecklistEvidenceRules.allowsManual(item, selected)) continue;
            var result = new CutoverChecklistItemResultDO(); BeanUtils.copyProperties(selected, result);
            result.setId(IdUtil.getSnowflakeNextId()); result.setChecklistItemId(item.getId()); result.setResultVersion(1);
            result.setFieldDefinitionId(CutoverChecklistResultStore.root(fields.forItem(item)).getId()); result.setBaseChecklistResultId(selected.getId());
            result.setSelectionStartedAt(command.time()); result.setSelectionEndedAt(null); result.setCurrentMarker(null);
            result.setSelectedBy(command.actorId()); result.setSelectionReasonCode("REVISION_COPY"); result.setCreatedBy(command.actorId()); result.setCreatedAt(command.time());
            if ("MANUAL".equals(selected.getResultSourceCode())) {
                var file = attachFile(new FileCopy(command, previous, item, custom.manualFile(selected)));
                require(results.insert(result) == 1, "修订附件结果创建失败");
                custom.insertManual(new CutoverCustomResultStore.Manual(result, result.getFieldDefinitionId(), file));
            } else if ("DIRECT".equals(selected.getResultSourceCode())) {
                require(results.insert(result) == 1, "修订填报结果创建失败");
                Object value = copyValue(new ValueCopy(command, previous, item,
                        answers.value(new CutoverChecklistResultStore.Read(previous, selected))));
                answers.insertAnswer(new CutoverChecklistResultStore.Write(item, result,
                        JsonUtils.toJsonString(Collections.singletonMap("value", value))));
            }
        }
    }

    private record ItemCopy(Copy command, CutoverChecklistItemDO source) { }
    private record CompatibleItems(CutoverChecklistItemDO source, CutoverChecklistItemDO target) { }

    private boolean compatible(CompatibleItems pair) {
        var source = pair.source(); var target = pair.target();
        return Objects.equals(source.getWorkModeCode(), target.getWorkModeCode())
                && Objects.equals(source.getItemTypeCode(), target.getItemTypeCode())
                && Objects.equals(source.getInterfaceFormatCode(), target.getInterfaceFormatCode())
                && ((Objects.equals(source.getItemDefinitionId(), target.getItemDefinitionId())
                        && Objects.equals(source.getItemDefinitionVersion(), target.getItemDefinitionVersion()))
                    || Objects.equals(CutoverFieldSchemaCodec.decode(fields.forItem(source)), CutoverFieldSchemaCodec.decode(fields.forItem(target))));
    }

    private CutoverChecklistItemDO copyItem(ItemCopy copy) {
        var command = copy.command(); var source = copy.source();
        var item = new CutoverChecklistItemDO(); BeanUtils.copyProperties(source, item);
        item.setId(IdUtil.getSnowflakeNextId()); item.setChecklistId(command.target().getId()); item.setVersion(0);
        item.setCreator(String.valueOf(command.actorId())); item.setUpdater(String.valueOf(command.actorId()));
        item.setCreateTime(command.time()); item.setUpdateTime(command.time());
        require(items.insert(item) == 1, "修订清单项创建失败");
        if (item.getItemDefinitionId() == null) {
            fields.insert(CutoverFieldSchemaCodec.Write.builder().tenantId(item.getTenantId()).customChecklistItemId(item.getId())
                    .businessCategoryCode("CUSTOM").itemTypeCode(item.getItemTypeCode())
                    .schema(CutoverFieldSchemaCodec.decode(fields.forItem(source))).build());
        }
        return item;
    }

    private record ValueCopy(Copy command, CutoverChecklistItemDO source, CutoverChecklistItemDO target, Object value) { }
    private Object copyValue(ValueCopy copy) {
        if (copy.value() instanceof CutoverChecklistFilePort.FileFact file) {
            return attachFile(new FileCopy(copy.command(), copy.source(), copy.target(), file));
        }
        if (copy.value() instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<Object, Object>();
            map.forEach((key, value) -> result.put(key, copyValue(new ValueCopy(copy.command(), copy.source(), copy.target(), value))));
            return result;
        }
        if (copy.value() instanceof List<?> list) {
            return list.stream().map(value -> copyValue(new ValueCopy(copy.command(), copy.source(), copy.target(), value))).toList();
        }
        return copy.value();
    }

    private record FileCopy(Copy command, CutoverChecklistItemDO source, CutoverChecklistItemDO target, CutoverChecklistFilePort.FileFact file) { }
    private CutoverChecklistFilePort.FileFact attachFile(FileCopy copy) {
        var file = copy.file(); require(file != null, "历史材料引用不存在");
        var version = file.fileFactVersion();
        var source = new FileArtifactVersionRevalidationQuery(file.artifactId(), file.versionNo(), "CUT", "CUTOVER_CHECKLIST_ITEM",
                copy.source().getId().toString(), "MANUAL_EVIDENCE", file.referenceKey(), FileActionCodes.READ,
                new FileFactVersion(version.artifactVersion(), version.referenceVersion(), version.availabilityVersion()), copy.command().target().getProjectScopeVersion());
        var target = new ExistingFileReferenceTarget("CUT", "CUTOVER_CHECKLIST_ITEM", copy.target().getId().toString(), "MANUAL_EVIDENCE",
                UUID.randomUUID().toString(), copy.command().target().getProjectScopeVersion());
        var attached = files.attachExistingVersions(new AttachExistingFileVersionsCommand("cutover-revision:" + copy.target().getId(),
                List.of(new AttachExistingFileVersionItem(source, target)))).getFirst();
        var actual = attached.fileFactVersion();
        return CutoverChecklistFilePort.FileFact.builder().artifactId(attached.artifactId()).versionNo(attached.versionNo())
                .referenceKey(attached.referenceKey()).scopeVersion(attached.scopeVersion()).sha256(attached.sha256())
                .fileFactVersion(new CutoverChecklistFilePort.FileFactVersion(actual.artifactVersion(), actual.referenceVersion(), actual.availabilityVersion())).build();
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new CutoverChecklistException(CutoverChecklistException.Code.STATE_CONFLICT, message);
    }
}
