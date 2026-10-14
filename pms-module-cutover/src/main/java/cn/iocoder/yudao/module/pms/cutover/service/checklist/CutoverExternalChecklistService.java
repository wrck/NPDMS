package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverExternalDataPort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverMetadataScalar;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.*;

/** 短事务预留查询 -> 事务外调用正式 Provider -> 短事务追加事实并受控采用。 */
@Service
@RequiredArgsConstructor
public class CutoverExternalChecklistService {
    private final CutoverChecklistApplicationService checklist;
    private final CutoverExternalBulletinStore storage;
    private final CutoverExternalDataDirectory directory;
    private final CutoverChecklistMapper checklists;
    private final CutoverChecklistItemMapper items;
    private final CutoverChecklistItemResultMapper results;
    private final CutoverChecklistExternalMappingMapper mappings;
    private final CutoverChecklistExternalQueryMapper queries;
    private final CutoverChecklistExternalQueryParameterMapper parameters;
    private final CutoverAssessmentDeviceMapper assessmentDevices;
    private final CutoverExternalChecklistMapper mutations;
    @jakarta.annotation.Resource
    private CutoverCommandExecutor commands;

    public record State(Long checklistId, Long checklistFactVersion, List<DeviceView> devices, List<ItemView> items) { }
    public record DeviceView(Long deviceId, String sn, String productModel, String currentSoftwareVersion, String targetSoftwareVersion) { }
    public record QueryView(Long id, Long deviceId, String versionContext, String softwareVersion, String productModel,
                            LocalDateTime startTime, LocalDateTime finishTime, Boolean succeeded, Integer returnedRowCount,
                            String failureCode, String failureDetail) { }
    public record BulletinView(Long sourceBulletinRowId, Long queryId, String number, String subject, String description,
                               String solution, Boolean involved, String measures) { }
    public record ItemView(Long itemId, String stableItemKey, String interfaceCode, boolean providerAvailable,
                           boolean manualFallback, Long resultId, Integer resultVersion, String resultSourceCode, boolean feedbackSaved,
                           boolean contextCurrent, List<Long> selectedQueryIds, List<QueryView> queries, List<BulletinView> bulletins) { }
    public record LoadResult(Long checklistId, Long checklistFactVersion, Long resultId, Integer resultVersion,
                             boolean selected, List<QueryView> queries) { }
    public record Preparation(Long checklistItemId, List<Long> queryIds) {
        public Preparation { queryIds = List.copyOf(queryIds); }
    }
    private record Prepared(LoadExternalChecklistCommand command, CutoverChecklistItemDO item,
                            List<CutoverChecklistExternalMappingDO> returnMappings, List<Invocation> invocations) { }
    private record Invocation(CutoverChecklistExternalQueryDO query, CutoverExternalDataPort.Request request,
                              String preparationFailure) { }
    private record Outcome(CutoverChecklistExternalQueryDO query, List<CutoverExternalBulletinStore.RawBulletin> rows) { }
    private record ParameterContext(CutoverChecklistApplicationService.ExternalDraft draft,
                                    CutoverChecklistDeviceContextDO device, CutoverAssessmentDeviceDO source, String version) { }

    public State state(CutoverChecklistContext context, Long taskId) {
        var authorized = checklist.getView(context.tenantId(), context.actorId(), taskId);
        var rows = storage.contexts(new CutoverExternalBulletinStore.ContextKey(context.tenantId(), authorized.checklistId()));
        Map<Long, CutoverAssessmentDeviceDO> frozen = rows.isEmpty() ? Map.of() : assessmentDevices.selectByIds(rows.stream()
                .map(CutoverChecklistDeviceContextDO::getAssessmentDeviceId).toList()).stream()
                .filter(row -> Objects.equals(row.getTenantId(), context.tenantId()))
                .collect(Collectors.toMap(CutoverAssessmentDeviceDO::getId, Function.identity()));
        List<DeviceView> deviceViews = rows.stream().map(row -> new DeviceView(row.getDeviceId(),
                frozen.containsKey(row.getAssessmentDeviceId()) ? frozen.get(row.getAssessmentDeviceId()).getSn() : null,
                row.getProductModel(), row.getCurrentSoftwareVersion(), row.getTargetSoftwareVersion())).toList();
        Map<Long, CutoverChecklistItemResultDO> current = results.selectCurrentByChecklist(
                new CutoverChecklistResultsQuery(context.tenantId(), authorized.checklistId())).stream()
                .collect(Collectors.toMap(CutoverChecklistItemResultDO::getChecklistItemId, Function.identity()));
        List<ItemView> itemViews = new ArrayList<>();
        for (var item : items.selectListByChecklist(new CutoverChecklistItemsQuery(context.tenantId(), authorized.checklistId()))) {
            if (!Boolean.TRUE.equals(item.getApplicableFlag()) || !"EXTERNAL".equals(item.getWorkModeCode())) continue;
            var definition = storage.definition(item);
            var result = current.get(item.getId());
            var read = new CutoverExternalBulletinStore.Read(item, result);
            List<CutoverChecklistExternalQueryDO> selected = result == null ? List.of() : storage.selectedQueries(read);
            boolean contextCurrent = false;
            if (result != null) {
                try { storage.requireCurrent(read); contextCurrent = true; }
                catch (CutoverChecklistException stale) { if (stale.getCode() != STATE_CONFLICT) throw stale; }
            }
            List<BulletinView> bulletinViews = result == null || !"EXTERNAL".equals(result.getResultSourceCode()) ? List.of()
                    : storage.resolvedRows(read).stream().map(row -> new BulletinView(
                            row.getSourceBulletinRowId() == null ? row.getId() : row.getSourceBulletinRowId(), row.getExternalQueryId(),
                            row.getBulletinNumber(), row.getBulletinSubject(), row.getBulletinDescription(), row.getBulletinSolution(),
                            row.getInvolvedFlag(), row.getMeasures())).toList();
            itemViews.add(new ItemView(item.getId(), item.getStableItemKey(), definition.getExternalInterfaceCode(),
                    directory.find(definition.getExternalInterfaceCode()).isPresent(), Boolean.TRUE.equals(definition.getExternalManualFallbackFlag()),
                    result == null ? null : result.getId(), result == null ? null : result.getResultVersion(),
                    result == null ? null : result.getResultSourceCode(),
                    result != null && "EXTERNAL_FEEDBACK".equals(result.getSelectionReasonCode()), contextCurrent,
                    selected.stream().map(CutoverChecklistExternalQueryDO::getId).toList(), storage.history(item).stream().map(CutoverExternalChecklistService::view).toList(), bulletinViews));
        }
        return new State(authorized.checklistId(), authorized.checklistFactVersion(), deviceViews, List.copyOf(itemViews));
    }

    public Long saveVersions(SaveChecklistDeviceVersionsCommand command) {
        checklist.authorizeDraftAccess(command);
        return commands.execute(CutoverCommandExecutor.Write.<Long>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_DEVICE_VERSIONS_SAVE")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(Long.class).operation(() -> {
            checklist.lockExternal(command);
            storage.saveVersions(command);
            touch(command);
            return command.expectedChecklistVersion() + 1;
        }).resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    public ChecklistItemCommandResult feedback(SaveExternalChecklistFeedbackCommand command) {
        checklist.authorizeDraftAccess(command);
        return commands.execute(CutoverCommandExecutor.Write.<ChecklistItemCommandResult>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_EXTERNAL_FEEDBACK_SAVE")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(ChecklistItemCommandResult.class).operation(() -> {
            var draft = checklist.lockExternal(command);
            var item = requireItem(draft, command.stableItemKey());
            var prior = results.selectCurrentForUpdate(new CutoverChecklistCurrentResultQuery(command.tenantId(), item.getId()));
            require(prior != null && Objects.equals(prior.getId(), command.expectedResultId()) && "EXTERNAL".equals(prior.getResultSourceCode()),
                    VERSION_CONFLICT, "当前外部结果已变化，请重新打开本项反馈");
            var row = storage.createResult(new CutoverExternalBulletinStore.ResultCreate(item, command.actorId(), LocalDateTime.now(),
                    "EXTERNAL_FEEDBACK", prior.getId(), true));
            storage.insertFeedback(new CutoverExternalBulletinStore.FeedbackWrite(new CutoverExternalBulletinStore.Read(item, prior), row, command.feedback()));
            touch(command);
            return new ChecklistItemCommandResult(command.checklistId(), command.expectedChecklistVersion() + 1, item.getId(), item.getStableItemKey(), row.getResultVersion());
        }).resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    public LoadResult load(LoadExternalChecklistCommand command) {
        checklist.authorizeDraftAccess(command);
        // 只在短事务中预留受控查询身份。外部查询始终在事务外执行，重试复用已落列的参数与 requestId。
        Preparation reservation = commands.execute(CutoverCommandExecutor.Write.<Preparation>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_EXTERNAL_QUERY_PREPARE")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(Preparation.class).operation(() -> {
                    var prepared = prepare(command);
                    return new Preparation(prepared.item().getId(), prepared.invocations().stream()
                            .map(value -> value.query().getId()).toList());
                }).resourceKey(value -> String.valueOf(command.checklistId())).build());
        Prepared prepared = restore(new Restore(command, reservation));
        boolean completed = prepared.invocations().stream().allMatch(value -> value.query().getLoadedChecklistResultId() != null);
        require(completed || prepared.invocations().stream().noneMatch(value -> value.query().getLoadedChecklistResultId() != null),
                STATE_CONFLICT, "外部查询完成事实不完整，不能重放部分结果");
        List<Outcome> outcomes = completed ? List.of() : prepared.invocations().stream()
                .map(invocation -> invoke(new Call(prepared, invocation))).toList();
        return commands.execute(CutoverCommandExecutor.Write.<LoadResult>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_EXTERNAL_QUERY_COMPLETE")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(LoadResult.class).operation(() -> {
                    if (completed) throw new IllegalStateException("外部查询完成事实缺少对应幂等结果");
                    return finish(prepared, outcomes);
                }).resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    private record Restore(LoadExternalChecklistCommand command, Preparation reservation) { }
    private Prepared restore(Restore restore) {
        var command = restore.command();
        var reservation = restore.reservation();
        var item = items.selectById(reservation.checklistItemId());
        require(item != null && Objects.equals(item.getTenantId(), command.tenantId())
                        && Objects.equals(item.getChecklistId(), command.checklistId())
                        && Objects.equals(item.getStableItemKey(), command.stableItemKey()),
                STATE_CONFLICT, "外部查询预留采集项不一致");
        List<CutoverChecklistExternalQueryDO> rows = queries.selectByIds(reservation.queryIds());
        require(!rows.isEmpty() && rows.size() == reservation.queryIds().size()
                        && rows.stream().allMatch(row -> Objects.equals(row.getTenantId(), command.tenantId())
                        && Objects.equals(row.getChecklistItemId(), item.getId()) && Objects.equals(row.getQueryUserId(), command.actorId())),
                STATE_CONFLICT, "外部查询预留身份不完整");
        rows = rows.stream().sorted(Comparator.comparing(CutoverChecklistExternalQueryDO::getQueryNo)).toList();
        Long definitionId = rows.getFirst().getItemDefinitionRevisionId();
        require(rows.stream().allMatch(row -> Objects.equals(row.getItemDefinitionRevisionId(), definitionId)),
                STATE_CONFLICT, "外部查询冻结定义不一致");
        var returnMappings = mappings.selectListByParents(CutoverChecklistExternalMappingListQuery.builder()
                .tenantId(command.tenantId()).itemDefinitionRevisionIds(Set.of(definitionId)).build()).stream()
                .filter(row -> "RETURN".equals(row.getMappingDirectionCode())).toList();
        var parameterRows = parameters.selectListByParents(CutoverChecklistExternalQueryParameterListQuery.builder()
                .tenantId(command.tenantId()).externalQueryIds(Set.copyOf(reservation.queryIds())).build());
        List<Invocation> invocations = new ArrayList<>();
        for (var row : rows) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (var parameter : parameterRows) {
                if (!Objects.equals(parameter.getExternalQueryId(), row.getId())) continue;
                var scalar = new CutoverMetadataScalar(parameter.getValueTypeCode(), parameter.getValueText(),
                        parameter.getValueNumber(), parameter.getValueBoolean(), parameter.getValueDatetime());
                values.put(parameter.getParameterName(), scalar.value());
            }
            invocations.add(new Invocation(row, new CutoverExternalDataPort.Request(command.tenantId(), command.actorId(),
                    command.taskId(), item.getId(), definitionId, row.getDeviceId(), row.getRequestId(),
                    Collections.unmodifiableMap(values)), row.getFinishTime() == null ? row.getFailureCode() : null));
        }
        return new Prepared(command, item, returnMappings, List.copyOf(invocations));
    }

    private Prepared prepare(LoadExternalChecklistCommand command) {
        var draft = checklist.lockExternal(command);
        var item = requireItem(draft, command.stableItemKey());
        var definition = storage.definition(item);
        require(present(definition.getExternalInterfaceCode()), FROZEN_CONFIGURATION_INVALID, "本采集项尚未配置正式外部接口");
        var contexts = storage.contexts(new CutoverExternalBulletinStore.ContextKey(command.tenantId(), command.checklistId()));
        require(!contexts.isEmpty(), STATE_CONFLICT, "本清单缺少冻结设备上下文");
        var mappingRows = mappings.selectListByParents(CutoverChecklistExternalMappingListQuery.builder()
                .tenantId(command.tenantId()).itemDefinitionRevisionIds(Set.of(definition.getId())).build());
        var provider = directory.find(definition.getExternalInterfaceCode());
        int next = storage.history(item).stream().mapToInt(CutoverChecklistExternalQueryDO::getQueryNo).max().orElse(0) + 1;
        List<Invocation> invocations = new ArrayList<>();
        for (var device : contexts) {
            String versionContext = CutoverExternalBulletinStore.versionContext(definition);
            String version = "TARGET".equals(versionContext) ? device.getTargetSoftwareVersion() : device.getCurrentSoftwareVersion();
            require(present(version), INVALID_REQUEST, "设备" + device.getDeviceId() + ("TARGET".equals(versionContext) ? "缺少目标软件版本" : "缺少当前软件版本"));
            var source = assessmentDevices.selectById(device.getAssessmentDeviceId());
            require(source != null && Objects.equals(source.getTenantId(), command.tenantId())
                            && Objects.equals(source.getAssessmentId(), draft.checklist().getAssessmentId())
                            && Objects.equals(source.getDeviceId(), device.getDeviceId()), STATE_CONFLICT, "设备冻结来源不完整");
            LocalDateTime now = LocalDateTime.now();
            var query = CutoverChecklistExternalQueryDO.builder().id(IdWorker.getId()).tenantId(command.tenantId())
                    .checklistItemId(item.getId()).itemDefinitionRevisionId(definition.getId()).deviceId(device.getDeviceId())
                    .interfaceCode(definition.getExternalInterfaceCode()).queryNo(next++).versionContextCode(versionContext)
                    .queriedSoftwareVersion(version).productModel(device.getProductModel()).requestId(UUID.randomUUID().toString())
                    .queryUserId(command.actorId()).startTime(now).createUserId(command.actorId()).createTime(now).build();
            List<CutoverChecklistExternalQueryParameterDO> parameterRows = new ArrayList<>();
            Map<String, Object> values = new LinkedHashMap<>();
            String failure = provider.isEmpty() ? "PROVIDER_UNAVAILABLE" : null;
            try {
                var controlled = new ParameterContext(draft, device, source, version);
                for (var mapping : mappingRows) {
                    if (!"PARAM".equals(mapping.getMappingDirectionCode())) continue;
                    Object value = parameterValue(controlled, mapping.getSourcePath());
                    require(value != null || !Boolean.TRUE.equals(mapping.getRequiredFlag()), INVALID_REQUEST, "外部参数缺少受控上下文值");
                    if (value == null) continue;
                    var scalar = CutoverMetadataScalar.from(value);
                    require(Objects.equals(scalar.type(), mapping.getValueTypeCode()), INVALID_REQUEST, "外部参数类型与配置不一致");
                    if (values.putIfAbsent(mapping.getTargetKey(), value) != null) throw new IllegalArgumentException("参数重复");
                    parameterRows.add(CutoverChecklistExternalQueryParameterDO.builder().tenantId(command.tenantId())
                            .externalQueryId(query.getId()).mappingId(mapping.getId()).parameterName(mapping.getTargetKey())
                            .valueTypeCode(scalar.type()).valueText(scalar.text()).valueNumber(scalar.number())
                            .valueBoolean(scalar.bool()).valueDatetime(scalar.datetime()).createUserId(command.actorId()).createTime(now).build());
                }
                require(mappingRows.stream().anyMatch(mapping -> "PARAM".equals(mapping.getMappingDirectionCode())
                                && "softwareVersion".equals(mapping.getSourcePath())),
                        INVALID_REQUEST, "公告查询参数必须使用本项查询软件版本");
                if (provider.isPresent()) validateParameters(provider.get().definition(), values);
            } catch (IllegalArgumentException | CutoverChecklistException invalid) { failure = "MAPPING_INVALID"; }
            query.setFailureCode(failure);
            queries.insert(query);
            parameterRows.forEach(parameters::insert);
            invocations.add(new Invocation(query, new CutoverExternalDataPort.Request(command.tenantId(), command.actorId(), command.taskId(),
                    item.getId(), definition.getId(), device.getDeviceId(), query.getRequestId(), Collections.unmodifiableMap(values)), failure));
        }
        touch(command);
        return new Prepared(command, item, mappingRows.stream().filter(row -> "RETURN".equals(row.getMappingDirectionCode())).toList(), List.copyOf(invocations));
    }

    private record Call(Prepared prepared, Invocation invocation) { }
    private Outcome invoke(Call call) {
        var invocation = call.invocation(); var query = invocation.query();
        try {
            if (invocation.preparationFailure() != null) throw new CutoverExternalDataPort.Failure(invocation.preparationFailure(),
                    "PROVIDER_UNAVAILABLE".equals(invocation.preparationFailure()) ? "该正式接口尚未接通" : "参数映射或上下文值不符合正式接口定义");
            var provider = directory.find(query.getInterfaceCode()).orElseThrow(() -> new CutoverExternalDataPort.Failure("PROVIDER_UNAVAILABLE", "该正式接口尚未接通"));
            validateReturnMappings(call.prepared().returnMappings(), provider.definition());
            var response = provider.query(invocation.request());
            if (response == null || response.rows() == null) throw new CutoverExternalDataPort.Failure("RESPONSE_INVALID", "外部响应缺少记录列表");
            List<CutoverExternalBulletinStore.RawBulletin> rows = new ArrayList<>();
            for (var row : response.rows()) rows.add(mapRow(new Returned(call.prepared().returnMappings(), query.getId(), row)));
            query.setSucceededFlag(true); query.setReturnedRowCount(rows.size()); query.setFinishTime(LocalDateTime.now());
            return new Outcome(query, List.copyOf(rows));
        } catch (CutoverExternalDataPort.Failure failure) {
            query.setFailureCode(failure.code() != null && failure.code().matches("[A-Z][A-Z0-9_]{0,63}") ? failure.code() : "EXTERNAL_CALL_FAILED");
            query.setFailureDetail(failure.getMessage());
        } catch (RuntimeException failure) {
            query.setFailureCode("EXTERNAL_CALL_FAILED"); query.setFailureDetail("外部调用失败，请重试或联系接口维护人员");
        }
        query.setSucceededFlag(false); query.setReturnedRowCount(null); query.setFinishTime(LocalDateTime.now());
        return new Outcome(query, List.of());
    }

    private LoadResult finish(Prepared prepared, List<Outcome> outcomes) {
        var command = prepared.command().afterReservation();
        boolean selected = true;
        try { checklist.lockExternal(command); }
        catch (CutoverChecklistException changed) {
            if (!Set.of(VERSION_CONFLICT, STATE_CONFLICT, DATA_SCOPE_FORBIDDEN, NOT_FOUND).contains(changed.getCode())) throw changed;
            selected = false;
        }
        require(Objects.equals(mutations.lockItem(new CutoverExternalItemQuery(command.tenantId(), prepared.item().getId())), prepared.item().getId()),
                STATE_CONFLICT, "查询来源采集项缺失");
        var result = storage.createResult(new CutoverExternalBulletinStore.ResultCreate(prepared.item(), command.actorId(), LocalDateTime.now(),
                "EXTERNAL_LOAD", null, selected));
        var terminalQueries = outcomes.stream().map(Outcome::query).toList();
        storage.insertLoaded(new CutoverExternalBulletinStore.Loaded(prepared.item(), result, terminalQueries,
                outcomes.stream().flatMap(outcome -> outcome.rows().stream()).toList()));
        for (var query : terminalQueries) {
            query.setLoadedChecklistResultId(result.getId());
            require(mutations.finishQuery(query) == 1, VERSION_CONFLICT, "外部查询已完成，不允许覆盖查询历史");
        }
        if (selected) touch(command);
        return new LoadResult(command.checklistId(), selected ? command.expectedChecklistVersion() + 1 : null,
                result.getId(), result.getResultVersion(), selected, terminalQueries.stream().map(CutoverExternalChecklistService::view).toList());
    }

    private record Returned(List<CutoverChecklistExternalMappingDO> mappings, Long queryId, CutoverExternalDataPort.Row row) { }
    private static void validateReturnMappings(List<CutoverChecklistExternalMappingDO> mappings, CutoverExternalDataPort.Definition definition) {
        Set<String> keys = mappings.stream().map(CutoverChecklistExternalMappingDO::getTargetKey).collect(Collectors.toSet());
        if (mappings.size() != 4 || !keys.equals(Set.of("number", "subject", "description", "solution"))) {
            throw new CutoverExternalDataPort.Failure("MAPPING_INVALID", "公告返回映射须完整对应四个原值字段");
        }
        for (var mapping : mappings) {
            boolean declared = definition.responseFields().stream().anyMatch(field -> field.key().equals(mapping.getSourcePath())
                    && "STRING".equals(field.valueType()));
            if (!declared || !"STRING".equals(mapping.getValueTypeCode()) || mapping.getSourcePath() == null
                    || !mapping.getSourcePath().matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
                throw new CutoverExternalDataPort.Failure("MAPPING_INVALID", "公告返回映射不属于正式接口定义");
            }
        }
    }
    private static CutoverExternalBulletinStore.RawBulletin mapRow(Returned input) {
        if (input.row() == null || input.row().values() == null) throw new CutoverExternalDataPort.Failure("RESPONSE_INVALID", "外部响应包含无效记录");
        Map<String, String> values = new LinkedHashMap<>();
        for (var mapping : input.mappings()) {
            if (!Set.of("number", "subject", "description", "solution").contains(mapping.getTargetKey())
                    || !"STRING".equals(mapping.getValueTypeCode())) throw new CutoverExternalDataPort.Failure("MAPPING_INVALID", "公告返回映射必须对应四个文本原值字段");
            Object value = path(input.row().values(), mapping.getSourcePath());
            if ((Boolean.TRUE.equals(mapping.getRequiredFlag()) && (value == null || value instanceof String text && text.isBlank()))
                    || (value != null && !(value instanceof String))) throw new CutoverExternalDataPort.Failure("RESPONSE_INVALID", "公告返回字段缺失或类型不符");
            values.put(mapping.getTargetKey(), (String) value);
        }
        if (!values.keySet().equals(Set.of("number", "subject", "description", "solution"))) throw new CutoverExternalDataPort.Failure("MAPPING_INVALID", "公告返回映射不完整");
        return new CutoverExternalBulletinStore.RawBulletin(input.queryId(), input.row().recordId(), input.row().recordVersion(),
                values.get("number"), values.get("subject"), values.get("description"), values.get("solution"));
    }

    private static Object parameterValue(ParameterContext source, String path) {
        return switch (path) {
            case "softwareVersion" -> source.version();
            case "device.id" -> source.device().getDeviceId();
            case "device.sn" -> source.source().getSn();
            case "device.productModel" -> source.device().getProductModel();
            case "device.currentSoftwareVersion" -> source.device().getCurrentSoftwareVersion();
            case "device.targetSoftwareVersion" -> source.device().getTargetSoftwareVersion();
            case "task.id" -> source.draft().task().getId();
            case "project.id" -> source.draft().task().getProjectId();
            default -> throw new IllegalArgumentException("参数映射不是受控上下文字段");
        };
    }

    private static Object path(Map<String, Object> values, String path) {
        if (path == null || !path.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) throw new CutoverExternalDataPort.Failure("MAPPING_INVALID", "返回映射路径非法");
        Object value = values;
        for (String part : path.split("\\.")) { if (!(value instanceof Map<?, ?> map)) return null; value = map.get(part); }
        return value;
    }

    private static void validateParameters(CutoverExternalDataPort.Definition definition, Map<String, Object> parameters) {
        Set<String> allowed = definition.parameters().stream().map(CutoverExternalDataPort.Field::key).collect(Collectors.toSet());
        if (!allowed.containsAll(parameters.keySet())) throw new IllegalArgumentException("参数不属于正式接口定义");
        for (var field : definition.parameters()) {
            Object value = parameters.get(field.key());
            if ((field.required() && value == null) || (value != null && !field.valueType().equals(CutoverMetadataScalar.from(value).type()))) {
                throw new IllegalArgumentException("正式接口参数缺失或类型不符");
            }
        }
    }

    private void touch(CutoverChecklistDraftMutation command) {
        require(checklists.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(), command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
    }
    private static CutoverChecklistItemDO requireItem(CutoverChecklistApplicationService.ExternalDraft draft, String key) {
        return draft.items().stream().filter(item -> Objects.equals(item.getStableItemKey(), key) && Boolean.TRUE.equals(item.getApplicableFlag()))
                .findFirst().orElseThrow(() -> new CutoverChecklistException(NOT_FOUND, "适用的清单项不存在"));
    }
    private static QueryView view(CutoverChecklistExternalQueryDO row) {
        return new QueryView(row.getId(), row.getDeviceId(), row.getVersionContextCode(), row.getQueriedSoftwareVersion(), row.getProductModel(),
                row.getStartTime(), row.getFinishTime(), row.getSucceededFlag(), row.getReturnedRowCount(), row.getFailureCode(), row.getFailureDetail());
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean condition, CutoverChecklistException.Code code, String detail) {
        if (!condition) throw new CutoverChecklistException(code, detail);
    }
}
