package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverRiskMatrixRules;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** 一个不可变结果根与其具名答案明细共同写入；旧 answer_snapshot 永不作为回退来源。 */
@Service
@RequiredArgsConstructor
public class CutoverChecklistResultStore {
    private final CutoverFieldDefinitionStore fields;
    private final CutoverCustomResultStore custom;
    private final CutoverResultCutoverBackgroundMapper cutoverBackground;
    private final CutoverResultBusinessSummaryMapper businessSummary;
    private final CutoverResultImpactScopeMapper impactScope;
    private final CutoverResultContinuityRequirementMapper continuityRequirement;
    private final CutoverResultInterruptionMapper interruption;
    private final CutoverResultCheckFeedbackMapper checkFeedback;
    private final CutoverResultSpareReferenceMapper spareReference;
    private final CutoverResultBulletinSummaryMapper bulletinSummary;
    private final CutoverResultDeviceLocationMapper deviceLocation;
    private final CutoverResultInterfacePlanMapper interfacePlan;
    private final CutoverResultIpPlanMapper ipPlan;
    private final CutoverResultPerformanceMapper performance;
    private final CutoverResultTechnicalBulletinMapper technicalBulletin;
    private final CutoverExternalBulletinStore externalBulletins;

    public record Write(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result, String answer) { }
    public record Read(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertAnswer(Write command) {
        if (!Objects.equals(command.item().getTenantId(), command.result().getTenantId())
                || !Objects.equals(command.item().getId(), command.result().getChecklistItemId())) {
            throw new IllegalArgumentException("答案与采集项不属于同一租户及对象");
        }
        if ("EXTERNAL".equals(command.item().getWorkModeCode())) {
            throw new IllegalArgumentException("外部原值只能由受控加载入口保存，反馈须引用原始公告行");
        }
        var definition = fields.forResult(command.result());
        var document = JsonUtils.parseTree(command.answer());
        if (document == null || !document.isObject()) throw new IllegalArgumentException("答案须为对象");
        JsonNode value = document.has("value") ? document.get("value") : document;
        if ("TABLE".equals(command.item().getInterfaceFormatCode()) && value.isTextual()) value = JsonUtils.parseTree(value.asText());
        if ("FILE".equals(command.item().getInterfaceFormatCode())) {
            custom.insert(new CutoverCustomResultStore.Write(command.item(), command.result(), definition, value));
            return;
        }
        var write = new CutoverBuiltinResultCodec.Write(command.result(), value);
        String family = family(definition);
        switch (family) {
            case "CUTOVER_BACKGROUND" -> cutoverBackground.insert(CutoverBuiltinResultCodec.cutoverBackground(write));
            case "BUSINESS_SUMMARY" -> businessSummary.insert(CutoverBuiltinResultCodec.businessSummary(write));
            case "IMPACT_SCOPE" -> impactScope.insert(CutoverBuiltinResultCodec.impactScope(write));
            case "CONTINUITY_REQUIREMENT" -> continuityRequirement.insert(CutoverBuiltinResultCodec.continuityRequirement(write));
            case "INTERRUPTION_COUNT" -> interruption.insert(CutoverBuiltinResultCodec.interruption(write));
            case "CHECK_FEEDBACK" -> checkFeedback.insert(CutoverBuiltinResultCodec.checkFeedback(write));
            case "MAJOR_PROJECT_SPARES" -> spareReference.insert(CutoverBuiltinResultCodec.spareReference(write));
            case "BULLETIN" -> bulletinSummary.insert(CutoverBuiltinResultCodec.bulletinSummary(write));
            case "DEVICE_LOCATION_PLAN" -> {
                var table = root(definition);
                var rows = rows(value);
                for (int i = 0; i < rows.size(); i++) deviceLocation.insert(CutoverBuiltinResultCodec.deviceLocation(
                        new CutoverBuiltinResultCodec.TableWrite(command.result(), rows.get(i), definition, table.getId(), i)));
            }
            case "INTERFACE_INTERCONNECT_PLAN" -> {
                var table = root(definition);
                var rows = rows(value);
                for (int i = 0; i < rows.size(); i++) interfacePlan.insert(CutoverBuiltinResultCodec.interfacePlan(
                        new CutoverBuiltinResultCodec.TableWrite(command.result(), rows.get(i), definition, table.getId(), i)));
            }
            case "IP_VLAN_PLAN" -> {
                var table = root(definition);
                var rows = rows(value);
                for (int i = 0; i < rows.size(); i++) ipPlan.insert(CutoverBuiltinResultCodec.ipPlan(
                        new CutoverBuiltinResultCodec.TableWrite(command.result(), rows.get(i), definition, table.getId(), i)));
            }
            case "PERFORMANCE_BASELINE" -> {
                var table = root(definition);
                var rows = rows(value);
                for (int i = 0; i < rows.size(); i++) performance.insert(CutoverBuiltinResultCodec.performance(
                        new CutoverBuiltinResultCodec.TableWrite(command.result(), rows.get(i), definition, table.getId(), i)));
            }
            case "CUSTOM" -> { }
            default -> throw new IllegalArgumentException("该固定业务类别须通过文件引用保存: " + family);
        }
        if ("BULLETIN".equals(family)) {
            var table = definition.fields().stream().filter(field -> "bulletins".equals(field.getFieldCode())
                    && Objects.equals(root(definition).getId(), field.getParentFieldId())).findFirst().orElseThrow();
            var rows = rows(value.get("bulletins"));
            for (int i = 0; i < rows.size(); i++) technicalBulletin.insert(CutoverBuiltinResultCodec.technicalBulletin(
                    new CutoverBuiltinResultCodec.TableWrite(command.result(), rows.get(i), definition, table.getId(), i)));
        }
        custom.insert(new CutoverCustomResultStore.Write(command.item(), command.result(), definition, value));
    }

    /** 给原 HTTP 展示/校验契约组装 value；不是数据库中的固定业务 JSON。 */
    public String answer(Read query) {
        var result = query.result();
        Object value = "MANUAL".equals(result.getResultSourceCode()) ? null : value(query);
        Map<String, Object> answer = new LinkedHashMap<>();
        if ("COLLECTION".equals(result.getResultSourceCode())) {
            answer.put("collectionTaskId", result.getCollectionTaskId()); answer.put("deviceId", result.getDeviceId());
            answer.put("commandTemplateId", result.getCommandTemplateId()); answer.put("technicalStatus", result.getCollectionStatusCode());
            answer.put("resultReferenceId", result.getCollectionResultReferenceId()); answer.put("resultVersion", result.getCollectionResultVersion());
            answer.put("failureCode", result.getCollectionFailureCode());
            if (result.getBaseChecklistResultId() != null) {
                Map<String, Object> analysis = new LinkedHashMap<>(); analysis.put("value", value); answer.put("analysis", analysis);
            }
        } else if ("MANUAL".equals(result.getResultSourceCode())) {
            answer.put("file", custom.manualFile(result));
            if (result.getOriginalFailedCollectionTaskId() != null) {
                answer.put("originalFailedCollectionTaskId", result.getOriginalFailedCollectionTaskId());
                answer.put("originalFailedResultVersion", result.getOriginalFailedResultVersion());
                answer.put("originalFailureCode", result.getOriginalFailureCode());
            }
        } else answer.put("value", value);
        return JsonUtils.toJsonString(answer);
    }

    public Object value(Read query) {
        var definition = fields.forResult(query.result());
        var result = query.result();
        Long tenantId = result.getTenantId(); Set<Long> ids = Set.of(result.getId());
        Object value = switch (family(definition)) {
            case "CUTOVER_BACKGROUND" -> CutoverBuiltinResultCodec.value(first(cutoverBackground.selectListByParents(
                    CutoverResultCutoverBackgroundListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "BUSINESS_SUMMARY" -> CutoverBuiltinResultCodec.value(first(businessSummary.selectListByParents(
                    CutoverResultBusinessSummaryListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "IMPACT_SCOPE" -> CutoverBuiltinResultCodec.value(first(impactScope.selectListByParents(
                    CutoverResultImpactScopeListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "CONTINUITY_REQUIREMENT" -> CutoverBuiltinResultCodec.value(first(continuityRequirement.selectListByParents(
                    CutoverResultContinuityRequirementListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "INTERRUPTION_COUNT" -> CutoverBuiltinResultCodec.value(first(interruption.selectListByParents(
                    CutoverResultInterruptionListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "CHECK_FEEDBACK" -> CutoverBuiltinResultCodec.value(first(checkFeedback.selectListByParents(
                    CutoverResultCheckFeedbackListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "MAJOR_PROJECT_SPARES" -> CutoverBuiltinResultCodec.value(first(spareReference.selectListByParents(
                    CutoverResultSpareReferenceListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build())));
            case "DEVICE_LOCATION_PLAN" -> deviceLocation.selectListByParents(
                    CutoverResultDeviceLocationListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build()).stream()
                    .map(CutoverBuiltinResultCodec::value).toList();
            case "INTERFACE_INTERCONNECT_PLAN" -> interfacePlan.selectListByParents(
                    CutoverResultInterfacePlanListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build()).stream()
                    .map(CutoverBuiltinResultCodec::value).toList();
            case "IP_VLAN_PLAN" -> ipPlan.selectListByParents(
                    CutoverResultIpPlanListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build()).stream()
                    .map(CutoverBuiltinResultCodec::value).toList();
            case "PERFORMANCE_BASELINE" -> performance.selectListByParents(
                    CutoverResultPerformanceListQuery.builder().tenantId(tenantId).checklistResultIds(ids).build()).stream()
                    .map(CutoverBuiltinResultCodec::value).toList();
            case "BULLETIN" -> {
                var summary = first(bulletinSummary.selectListByParents(CutoverResultBulletinSummaryListQuery.builder()
                        .tenantId(tenantId).checklistResultIds(ids).build()));
                Map<String, Object> data = new LinkedHashMap<>();
                if (summary != null) {
                    data.put("hasBulletins", summary.getHasBulletinsFlag()); data.put("queryBasis", summary.getManualQueryBasis());
                }
                if ("EXTERNAL".equals(result.getResultSourceCode())) {
                    data.put("queryBasis", externalBulletins.selectedQueries(new CutoverExternalBulletinStore.Read(query.item(), result))
                            .stream().map(source -> "设备" + source.getDeviceId() + " / "
                                    + ("TARGET".equals(source.getVersionContextCode()) ? "目标版本" : "当前版本")
                                    + source.getQueriedSoftwareVersion() + " / " + source.getInterfaceCode() + " / " + source.getStartTime())
                            .collect(java.util.stream.Collectors.joining("；")));
                }
                var rows = "EXTERNAL".equals(result.getResultSourceCode())
                        ? externalBulletins.resolvedRows(new CutoverExternalBulletinStore.Read(query.item(), result))
                        : technicalBulletin.selectListByParents(CutoverResultTechnicalBulletinListQuery.builder()
                                .tenantId(tenantId).checklistResultIds(ids).build());
                data.put("bulletins", rows.stream().map(CutoverBuiltinResultCodec::value).toList());
                yield data;
            }
            default -> null;
        };
        return custom.merge(new CutoverCustomResultStore.Read(query.item(), result, definition, value));
    }

    public CutoverResultCheckFeedbackDO feedback(CutoverChecklistItemResultDO result) {
        return first(checkFeedback.selectListByParents(CutoverResultCheckFeedbackListQuery.builder()
                .tenantId(result.getTenantId()).checklistResultIds(Set.of(result.getId())).build()));
    }

    static CutoverChecklistFieldDefinitionDO root(CutoverFieldSchemaCodec.Definition definition) {
        var roots = definition.fields().stream().filter(field -> field.getParentFieldId() == null).toList();
        if (roots.size() != 1) throw new IllegalStateException("精确字段定义缺失或有多个根");
        return roots.getFirst();
    }
    static String family(CutoverFieldSchemaCodec.Definition definition) {
        String code = root(definition).getBuiltinFieldCode();
        if (code == null) return "CUSTOM";
        if (Set.of("CURRENT_VERSION_BULLETIN", "TARGET_VERSION_BULLETIN").contains(code)) return "BULLETIN";
        if ("DUAL_MACHINE_CHECK".equals(code) || (CutoverRiskMatrixRules.REQUIRED_RISK_CATEGORIES.contains(code)
                && !Set.of("MAJOR_PROJECT_SPARES", "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
                "CONFIG_BACKUP", "ROOM_OPERATION_COMMITMENT").contains(code))) return "CHECK_FEEDBACK";
        return code;
    }
    private static List<JsonNode> rows(JsonNode value) {
        if (value == null || value.isNull()) return List.of();
        JsonNode rows = value.isArray() ? value : value.get("rows");
        if (rows == null || !rows.isArray()) throw new IllegalArgumentException("业务表格须为行数组");
        List<JsonNode> result = new ArrayList<>(); rows.forEach(result::add); return result;
    }
    private static <T> T first(List<T> rows) { return rows.isEmpty() ? null : rows.getFirst(); }
}
