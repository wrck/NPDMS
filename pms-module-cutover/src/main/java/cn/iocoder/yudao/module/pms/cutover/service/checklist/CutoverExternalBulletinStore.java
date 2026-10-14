package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistDeviceVersionsCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveExternalChecklistFeedbackCommand.Feedback;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.*;

/** 外部原值、查询采用关系与反馈分别追加。普通保存入口无权填写外部原值。 */
@Service
@RequiredArgsConstructor
public class CutoverExternalBulletinStore {
    private final CutoverChecklistDeviceContextMapper devices;
    private final CutoverChecklistItemDefinitionRevisionMapper definitions;
    private final CutoverChecklistExternalQueryMapper queries;
    private final CutoverChecklistResultQueryReferenceMapper references;
    private final CutoverResultTechnicalBulletinMapper bulletins;
    private final CutoverResultBulletinSummaryMapper summaries;
    private final CutoverChecklistItemResultMapper results;
    private final CutoverExternalChecklistMapper mutations;
    private final CutoverFieldDefinitionStore fields;

    public record ContextKey(Long tenantId, Long checklistId) { }
    public record Read(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result) { }
    public record ResultCreate(CutoverChecklistItemDO item, Long actorId, LocalDateTime time,
                               String reason, Long baseResultId, boolean selected) { }
    public record Loaded(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result,
                         List<CutoverChecklistExternalQueryDO> queries, List<RawBulletin> bulletins) { }
    public record RawBulletin(Long queryId, String recordId, String recordVersion, String number,
                              String subject, String description, String solution) { }
    public record FeedbackWrite(Read source, CutoverChecklistItemResultDO result, List<Feedback> feedback) { }
    public record ManualWrite(Read source, CutoverChecklistItemResultDO result) { }

    public List<CutoverChecklistDeviceContextDO> contexts(ContextKey key) {
        requireTenant(key.tenantId());
        return devices.selectListByParents(CutoverChecklistDeviceContextListQuery.builder()
                .tenantId(key.tenantId()).checklistIds(Set.of(key.checklistId())).build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveVersions(SaveChecklistDeviceVersionsCommand command) {
        var existing = contexts(new ContextKey(command.tenantId(), command.checklistId()));
        require(command.devices() != null && !existing.isEmpty(), INVALID_REQUEST, "设备版本上下文不能为空");
        Set<Long> submitted = new HashSet<>();
        for (var device : command.devices()) {
            require(device != null && device.deviceId() != null && submitted.add(device.deviceId()),
                    INVALID_REQUEST, "设备版本上下文重复或非法");
        }
        require(submitted.equals(existing.stream().map(CutoverChecklistDeviceContextDO::getDeviceId).collect(Collectors.toSet())),
                INVALID_REQUEST, "设备版本上下文必须对应本清单完整的冻结设备范围");
        for (var device : command.devices()) {
            require(mutations.updateDevice(new CutoverExternalDeviceUpdate(command.tenantId(), command.checklistId(),
                    device.deviceId(), trim(device.productModel()), trim(device.currentSoftwareVersion()),
                    trim(device.targetSoftwareVersion()), command.actorId(), LocalDateTime.now())) == 1,
                    VERSION_CONFLICT, "设备版本上下文已变化");
        }
    }

    public CutoverChecklistItemDefinitionRevisionDO definition(CutoverChecklistItemDO item) {
        requireTenant(item.getTenantId());
        require("EXTERNAL".equals(item.getWorkModeCode()) && item.getItemDefinitionId() != null,
                STATE_CONFLICT, "当前采集项不使用外部数据源");
        var row = definitions.selectById(item.getItemDefinitionId());
        require(row != null && Objects.equals(row.getTenantId(), item.getTenantId())
                        && Objects.equals(row.getItemDefinitionVersion(), item.getItemDefinitionVersion())
                        && "EXTERNAL".equals(row.getWorkModeCode()),
                FROZEN_CONFIGURATION_INVALID, "外部采集项精确定义不可用");
        require(Set.of("CURRENT_VERSION_BULLETIN", "TARGET_VERSION_BULLETIN").contains(row.getBusinessCategoryCode()),
                FROZEN_CONFIGURATION_INVALID, "当前外部加载入口仅支持已定义的当前/目标版本技术公告");
        return row;
    }

    public static String versionContext(CutoverChecklistItemDefinitionRevisionDO definition) {
        return "TARGET_VERSION_BULLETIN".equals(definition.getBusinessCategoryCode()) ? "TARGET" : "CURRENT";
    }

    public List<CutoverChecklistExternalQueryDO> history(CutoverChecklistItemDO item) {
        return queries.selectListByParents(CutoverChecklistExternalQueryListQuery.builder()
                .tenantId(item.getTenantId()).checklistItemIds(Set.of(item.getId())).build());
    }

    public List<CutoverChecklistExternalQueryDO> selectedQueries(Read read) {
        requireSameItem(read);
        var refs = references.selectListByParents(CutoverChecklistResultQueryReferenceListQuery.builder()
                .tenantId(read.result().getTenantId()).checklistResultIds(Set.of(read.result().getId())).build());
        Map<Long, CutoverChecklistExternalQueryDO> all = history(read.item()).stream()
                .collect(Collectors.toMap(CutoverChecklistExternalQueryDO::getId, Function.identity()));
        List<CutoverChecklistExternalQueryDO> selected = new ArrayList<>();
        for (var ref : refs) {
            var query = all.get(ref.getExternalQueryId());
            require(Objects.equals(ref.getTenantId(), read.item().getTenantId())
                            && Objects.equals(ref.getChecklistResultId(), read.result().getId()) && query != null,
                    STATE_CONFLICT, "外部结果的查询来源引用不完整");
            String purpose = "MANUAL".equals(read.result().getResultSourceCode()) && Boolean.FALSE.equals(query.getSucceededFlag())
                    ? "FAILED_FALLBACK" : "SOURCE";
            require(purpose.equals(ref.getReferencePurposeCode()), STATE_CONFLICT, "外部结果的来源用途不一致");
            selected.add(query);
        }
        require(selected.size() == selected.stream().map(CutoverChecklistExternalQueryDO::getId).distinct().count(),
                STATE_CONFLICT, "外部结果查询来源重复");
        return List.copyOf(selected);
    }

    /** 提交及反馈复验：旧结果保留，但不能用于已改变的设备版本条件。 */
    public List<CutoverChecklistExternalQueryDO> requireCurrent(Read read) {
        var definition = definition(read.item());
        var context = contexts(new ContextKey(read.item().getTenantId(), read.item().getChecklistId()));
        var selected = selectedQueries(read);
        require(!context.isEmpty() && selected.size() == context.size(), STATE_CONFLICT, "外部查询未覆盖完整设备范围");
        Map<Long, CutoverChecklistDeviceContextDO> byDevice = context.stream()
                .collect(Collectors.toMap(CutoverChecklistDeviceContextDO::getDeviceId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (var query : selected) {
            var device = byDevice.get(query.getDeviceId());
            String version = device == null ? null : "TARGET".equals(versionContext(definition))
                    ? device.getTargetSoftwareVersion() : device.getCurrentSoftwareVersion();
            require(device != null && seen.add(device.getDeviceId()) && present(version)
                            && Objects.equals(query.getTenantId(), read.item().getTenantId())
                            && Objects.equals(query.getChecklistItemId(), read.item().getId())
                            && Objects.equals(query.getItemDefinitionRevisionId(), definition.getId())
                            && Objects.equals(query.getInterfaceCode(), definition.getExternalInterfaceCode())
                            && Objects.equals(query.getVersionContextCode(), versionContext(definition))
                            && Objects.equals(query.getQueriedSoftwareVersion(), version)
                            && Objects.equals(query.getProductModel(), device.getProductModel())
                            && query.getSucceededFlag() != null && query.getFinishTime() != null
                            && (Boolean.TRUE.equals(query.getSucceededFlag())
                                ? query.getReturnedRowCount() != null && query.getReturnedRowCount() >= 0 && query.getFailureCode() == null
                                : query.getReturnedRowCount() == null && present(query.getFailureCode())),
                    STATE_CONFLICT, "外部查询条件已变化或查询尚未完成，请按当前设备版本重新加载");
        }
        return selected;
    }

    public void requireManual(Read read) {
        require(read.result() != null && Boolean.TRUE.equals(definition(read.item()).getExternalManualFallbackFlag()),
                STATE_CONFLICT, "当前采集项未允许人工替代外部加载");
        require(requireCurrent(read).stream().anyMatch(query -> Boolean.FALSE.equals(query.getSucceededFlag())),
                STATE_CONFLICT, "人工替代必须引用本次条件下实际失败的外部查询");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void referenceManual(ManualWrite write) {
        requireSameItem(new Read(write.source().item(), write.result()));
        requireManual(write.source());
        appendReferences(new Loaded(write.source().item(), write.result(), selectedQueries(write.source()), List.of()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CutoverChecklistItemResultDO createResult(ResultCreate command) {
        var item = command.item();
        var lookup = new CutoverChecklistCurrentResultQuery(item.getTenantId(), item.getId());
        var current = results.selectCurrentForUpdate(lookup);
        if (current != null && command.selected()) {
            require(results.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(item.getTenantId(), current.getId(), command.time())) == 1,
                    VERSION_CONFLICT, "当前清单结果已变化");
        }
        var row = CutoverChecklistItemResultDO.builder().id(IdWorker.getId()).tenantId(item.getTenantId())
                .checklistItemId(item.getId()).fieldDefinitionId(CutoverChecklistResultStore.root(fields.forItem(item)).getId())
                .resultVersion(results.selectMaxVersion(lookup) + 1).resultSourceCode("EXTERNAL")
                .baseChecklistResultId(command.baseResultId()).selectionReasonCode(command.reason())
                .selectionStartedAt(command.time()).selectionEndedAt(command.selected() ? null : command.time())
                .selectedBy(command.actorId()).createdBy(command.actorId()).createdAt(command.time()).deleted(false).build();
        return row;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertLoaded(Loaded command) {
        requireSameItem(new Read(command.item(), command.result()));
        var result = command.result();
        result.setExternalSourceCode(definition(command.item()).getExternalInterfaceCode());
        result.setQueriedAt(command.queries().stream().map(CutoverChecklistExternalQueryDO::getStartTime).max(Comparator.naturalOrder()).orElseThrow());
        boolean success = command.queries().stream().allMatch(query -> Boolean.TRUE.equals(query.getSucceededFlag()));
        result.setLoadFailureCode(success ? null : "EXTERNAL_LOAD_FAILED");
        require(results.insert(result) == 1, STATE_CONFLICT, "外部结果保存失败");
        summaries.insert(CutoverResultBulletinSummaryDO.builder().tenantId(result.getTenantId()).checklistResultId(result.getId())
                .hasBulletinsFlag(success ? !command.bulletins().isEmpty() : null)
                .createUserId(result.getCreatedBy()).createTime(result.getCreatedAt()).build());
        Long tableId = bulletinTable(result);
        int index = 0;
        Set<Long> successfulQueries = command.queries().stream().filter(query -> Boolean.TRUE.equals(query.getSucceededFlag()))
                .map(CutoverChecklistExternalQueryDO::getId).collect(Collectors.toSet());
        for (var source : command.bulletins()) {
            require(successfulQueries.contains(source.queryId()), STATE_CONFLICT, "公告原值必须来自本结果的成功查询");
            bulletins.insert(CutoverResultTechnicalBulletinDO.builder().tenantId(result.getTenantId()).checklistResultId(result.getId())
                    .tableFieldDefinitionId(tableId).rowNo(index++).externalQueryId(source.queryId())
                    .externalRecordId(source.recordId()).externalRecordVersion(source.recordVersion())
                    .bulletinNumber(source.number()).bulletinSubject(source.subject()).bulletinDescription(source.description())
                    .bulletinSolution(source.solution()).createUserId(result.getCreatedBy()).createTime(result.getCreatedAt()).build());
        }
        appendReferences(command);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertFeedback(FeedbackWrite command) {
        requireSameItem(new Read(command.source().item(), command.result()));
        var selected = requireCurrent(command.source());
        require(selected.stream().allMatch(query -> Boolean.TRUE.equals(query.getSucceededFlag())),
                STATE_CONFLICT, "外部查询仍有失败，须重新加载或按配置使用人工证据");
        var sourceRows = resolvedRows(command.source());
        require(command.feedback() != null && command.feedback().size() == sourceRows.size(), INVALID_REQUEST, "反馈须对应本次加载的全部公告");
        Map<Long, Feedback> feedback = new LinkedHashMap<>();
        for (var row : command.feedback()) {
            require(row != null && row.sourceBulletinRowId() != null && feedback.putIfAbsent(row.sourceBulletinRowId(), row) == null,
                    INVALID_REQUEST, "公告反馈来源重复或缺失");
        }
        Set<Long> actual = sourceRows.stream().map(row -> row.getSourceBulletinRowId() == null ? row.getId() : row.getSourceBulletinRowId()).collect(Collectors.toSet());
        require(actual.equals(feedback.keySet()), INVALID_REQUEST, "不能替换、增加或移除外部公告原始行");
        var result = command.result();
        result.setExternalSourceCode(command.source().result().getExternalSourceCode());
        result.setQueriedAt(command.source().result().getQueriedAt());
        require(results.insert(result) == 1, STATE_CONFLICT, "公告反馈保存失败");
        summaries.insert(CutoverResultBulletinSummaryDO.builder().tenantId(result.getTenantId()).checklistResultId(result.getId())
                .hasBulletinsFlag(!sourceRows.isEmpty()).createUserId(result.getCreatedBy()).createTime(result.getCreatedAt()).build());
        int index = 0;
        for (var source : sourceRows) {
            Long originalId = source.getSourceBulletinRowId() == null ? source.getId() : source.getSourceBulletinRowId();
            var answer = feedback.get(originalId);
            bulletins.insert(CutoverResultTechnicalBulletinDO.builder().tenantId(result.getTenantId()).checklistResultId(result.getId())
                    .tableFieldDefinitionId(source.getTableFieldDefinitionId()).rowNo(index++).sourceBulletinRowId(originalId)
                    .involvedFlag(answer.involved()).measures(trim(answer.measures()))
                    .createUserId(result.getCreatedBy()).createTime(result.getCreatedAt()).build());
        }
        appendReferences(new Loaded(command.source().item(), result, selected, List.of()));
    }

    public List<CutoverResultTechnicalBulletinDO> resolvedRows(Read read) {
        requireSameItem(read);
        var rows = bulletins.selectListByParents(CutoverResultTechnicalBulletinListQuery.builder()
                .tenantId(read.result().getTenantId()).checklistResultIds(Set.of(read.result().getId())).build());
        var selected = selectedQueries(read).stream().collect(Collectors.toMap(CutoverChecklistExternalQueryDO::getId, Function.identity()));
        Long expectedTableId = bulletinTable(read.result());
        for (var row : rows) {
            require(Objects.equals(row.getTenantId(), read.result().getTenantId())
                            && Objects.equals(row.getChecklistResultId(), read.result().getId())
                            && Objects.equals(row.getTableFieldDefinitionId(), expectedTableId),
                    STATE_CONFLICT, "公告行不属于本结果的精确字段定义");
            if (row.getSourceBulletinRowId() == null) {
                var query = selected.get(row.getExternalQueryId());
                require(query != null && Boolean.TRUE.equals(query.getSucceededFlag())
                                && Objects.equals(query.getLoadedChecklistResultId(), row.getChecklistResultId()),
                        STATE_CONFLICT, "公告原始行不属于本次成功查询");
            }
        }
        Set<Long> sourceIds = rows.stream().map(CutoverResultTechnicalBulletinDO::getSourceBulletinRowId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (sourceIds.isEmpty()) return rows;
        Map<Long, CutoverResultTechnicalBulletinDO> originals = bulletins.selectByIds(sourceIds).stream()
                .collect(Collectors.toMap(CutoverResultTechnicalBulletinDO::getId, Function.identity()));
        List<CutoverResultTechnicalBulletinDO> resolved = new ArrayList<>();
        for (var row : rows) {
            if (row.getSourceBulletinRowId() == null) { resolved.add(row); continue; }
            var original = originals.get(row.getSourceBulletinRowId());
            var query = original == null ? null : selected.get(original.getExternalQueryId());
            require(original != null && Objects.equals(original.getTenantId(), read.result().getTenantId())
                            && original.getSourceBulletinRowId() == null && query != null
                            && Boolean.TRUE.equals(query.getSucceededFlag())
                            && Objects.equals(query.getLoadedChecklistResultId(), original.getChecklistResultId())
                            && Objects.equals(original.getTableFieldDefinitionId(), row.getTableFieldDefinitionId())
                            && row.getBulletinNumber() == null && row.getBulletinSubject() == null
                            && row.getBulletinDescription() == null && row.getBulletinSolution() == null,
                    STATE_CONFLICT, "外部公告原值来源不完整或被修改");
            var value = CutoverResultTechnicalBulletinDO.builder().id(row.getId()).tenantId(row.getTenantId())
                    .checklistResultId(row.getChecklistResultId()).tableFieldDefinitionId(row.getTableFieldDefinitionId()).rowNo(row.getRowNo())
                    .sourceBulletinRowId(original.getId()).externalQueryId(original.getExternalQueryId())
                    .externalRecordId(original.getExternalRecordId()).externalRecordVersion(original.getExternalRecordVersion())
                    .bulletinNumber(original.getBulletinNumber()).bulletinSubject(original.getBulletinSubject())
                    .bulletinDescription(original.getBulletinDescription()).bulletinSolution(original.getBulletinSolution())
                    .involvedFlag(row.getInvolvedFlag()).measures(row.getMeasures()).extensionValues(row.getExtensionValues())
                    .createUserId(row.getCreateUserId()).createTime(row.getCreateTime()).build();
            resolved.add(value);
        }
        return List.copyOf(resolved);
    }

    public void validateSubmission(Read read) {
        if ("MANUAL".equals(read.result().getResultSourceCode())) { requireManual(read); return; }
        require("EXTERNAL".equals(read.result().getResultSourceCode())
                        && "EXTERNAL_FEEDBACK".equals(read.result().getSelectionReasonCode())
                        && requireCurrent(read).stream().allMatch(query -> Boolean.TRUE.equals(query.getSucceededFlag())),
                STATE_CONFLICT, "外部加载成功后仍须保存本次风险反馈");
        var rows = resolvedRows(read);
        require(requireCurrent(read).stream().mapToInt(query -> query.getReturnedRowCount()).sum() == rows.size(),
                STATE_CONFLICT, "公告行数与原始查询事实不一致");
        for (var row : rows) {
            require(row.getInvolvedFlag() != null && (!row.getInvolvedFlag() || present(row.getMeasures())),
                    STATE_CONFLICT, "请填写每条公告是否涉及，并为涉及项填写措施");
        }
    }

    private void appendReferences(Loaded command) {
        int index = 0;
        for (var query : command.queries()) {
            references.insert(CutoverChecklistResultQueryReferenceDO.builder().tenantId(command.result().getTenantId())
                    .checklistResultId(command.result().getId()).externalQueryId(query.getId())
                    .referencePurposeCode("MANUAL".equals(command.result().getResultSourceCode()) && Boolean.FALSE.equals(query.getSucceededFlag())
                            ? "FAILED_FALLBACK" : "SOURCE").sortOrder(index++)
                    .createUserId(command.result().getCreatedBy()).createTime(command.result().getCreatedAt()).build());
        }
    }

    private Long bulletinTable(CutoverChecklistItemResultDO result) {
        return fields.forResult(result).fields().stream().filter(field -> "bulletins".equals(field.getFieldCode())
                && Objects.equals(field.getParentFieldId(), result.getFieldDefinitionId())).map(CutoverChecklistFieldDefinitionDO::getId)
                .findFirst().orElseThrow(() -> new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "公告表格定义缺失"));
    }

    private static void requireSameItem(Read read) {
        require(read != null && read.item() != null && read.result() != null
                && Objects.equals(read.item().getTenantId(), read.result().getTenantId())
                && Objects.equals(read.item().getId(), read.result().getChecklistItemId()), STATE_CONFLICT, "外部结果与清单项不一致");
        requireTenant(read.item().getTenantId());
    }
    private static void requireTenant(Long tenantId) {
        require(!cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.isIgnore()
                        && Objects.equals(tenantId, cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()),
                DATA_SCOPE_FORBIDDEN, "外部查询上下文不属于当前租户");
    }
    private static String trim(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static boolean present(String text) { return text != null && !text.isBlank(); }
    private static void require(boolean condition, CutoverChecklistException.Code code, String detail) {
        if (!condition) throw new CutoverChecklistException(code, detail);
    }
}
