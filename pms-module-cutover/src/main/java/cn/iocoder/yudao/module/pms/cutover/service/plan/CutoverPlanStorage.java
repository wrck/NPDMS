package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultVersionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStorageMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** 方案正文与精确来源的关系存储。JSON仅在请求、响应和文档生成边界组装。 */
@Service
@RequiredArgsConstructor
public class CutoverPlanStorage {
    private final CutoverPlanDeviceMapper devices;
    private final CutoverPlanScheduleMapper schedules;
    private final CutoverPlanFileMapper files;
    private final CutoverPlanRiskMitigationMapper risks;
    private final CutoverPlanSupportContactChangeMapper contactChanges;
    private final CutoverSupportArrangementMapper supports;
    private final CutoverAssessmentDeviceMapper assessmentDevices;
    private final CutoverChecklistItemMapper checklistItems;
    private final CutoverChecklistItemResultMapper checklistResults;
    private final CutoverChecklistResultStore resultStorage;
    private final CutoverConfigurationMetadataStore configuration;
    private final CutoverPlanStorageMapper drafts;

    @Builder
    public record Write(CutoverPlanRevisionDO plan, CutoverPlanSourcePort.SourceSnapshot source,
                        JsonNode content, Long actorId, LocalDateTime time) {}

    @Builder
    public record RootSource(CutoverPlanRevisionDO plan, CutoverTaskDO task,
                             CutoverPlanSourcePort.SourceSnapshot source) {}

    public static void applySource(RootSource value) {
        var plan = value.plan(); var source = value.source();
        plan.setSourceTaskVersion(source.taskVersion());
        plan.setSourceProjectId(source.projectId());
        plan.setSourceProjectVersion(source.projectVersion().longValue());
        plan.setSourceProjectScopeVersion(source.projectScopeVersion());
        plan.setSourceCutoverTypeCode(value.task().getCutoverType());
    }

    public static void applyContent(CutoverPlanRevisionDO plan, JsonNode content) {
        JsonNode overview = content == null ? null : content.get("overview");
        plan.setProjectDescription(text(overview, "projectDescription"));
        plan.setRollbackDescription(text(content, "rollbackDescription"));
        var contact = CutoverPlanContactPolicy.selection(content == null ? null : content.get("customerContactReference"));
        plan.setCustomerContactId(contact == null ? null : contact.contactId());
        plan.setCustomerContactVersion(contact == null ? null : contact.contactVersion().longValue());
    }

    public static void applyFile(CutoverPlanRevisionDO plan, CutoverPlanFilePort.FileFact file) {
        plan.setFileArtifactId(file == null ? null : file.artifactId());
        plan.setFileVersionNo(file == null ? null : file.versionNo());
        plan.setFileReferenceKey(file == null ? null : file.referenceKey());
        plan.setFileArtifactFactVersion(file == null ? null : file.fileFactVersion().artifactVersion().longValue());
        plan.setFileReferenceFactVersion(file == null ? null : file.fileFactVersion().referenceVersion().longValue());
        plan.setFileAvailabilityFactVersion(file == null ? null : file.fileFactVersion().availabilityVersion().longValue());
        plan.setFileScopeVersion(file == null ? null : file.scopeVersion());
        plan.setFileSha256(file == null ? null : file.sha256());
    }

    public static CutoverPlanFilePort.FileFactVersion fileVersion(CutoverPlanRevisionDO plan) {
        return new CutoverPlanFilePort.FileFactVersion(Math.toIntExact(plan.getFileArtifactFactVersion()),
                Math.toIntExact(plan.getFileReferenceFactVersion()), Math.toIntExact(plan.getFileAvailabilityFactVersion()));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void replaceDraft(Write command) {
        var plan = command.plan();
        requireTenant(plan.getTenantId());
        if (!"DRAFT".equals(plan.getStatusCode()) || !Objects.equals(plan.getCurrentMarker(), 1)
                || !Objects.equals(plan.getCutoverTaskId(), command.source().taskId())
                || !Objects.equals(plan.getAssessmentId(), command.source().assessmentId())) {
            throw new IllegalArgumentException("仅能替换同一来源的方案草稿");
        }
        var query = new CutoverPlanChildrenQuery(plan.getTenantId(), plan.getId());
        drafts.deleteDraftDevices(query); drafts.deleteDraftSchedules(query);
        drafts.deleteDraftFiles(query); drafts.deleteDraftRisks(query);
        JsonNode overview = command.content() == null ? null : command.content().get("overview");
        Map<Long, JsonNode> details = new HashMap<>();
        if (overview != null) overview.path("deviceDetails").forEach(row -> details.put(row.path("deviceId").asLong(), row));
        Map<Long, Long> assessed = assessmentDevices.selectListByParents(CutoverAssessmentDeviceListQuery.builder()
                .tenantId(plan.getTenantId()).assessmentIds(Set.of(plan.getAssessmentId())).build()).stream()
                .collect(Collectors.toMap(CutoverAssessmentDeviceDO::getDeviceId, CutoverAssessmentDeviceDO::getId));
        int order = 0;
        for (var device : command.source().devices()) {
            var detail = details.get(device.deviceId());
            Long assessedId = assessed.get(device.deviceId());
            if (assessedId == null) throw new IllegalArgumentException("方案设备缺少对应评估事实");
            var row = CutoverPlanDeviceDO.builder().planRevisionId(plan.getId()).assessmentDeviceId(assessedId)
                    .deviceId(device.deviceId()).sn(device.serialNumber()).projectAssignmentVersion(device.projectAssignmentVersion())
                    .deviceTypeCode(device.deviceTypeCode()).deviceTypeSourceVersion(device.deviceTypeSourceVersion())
                    .productModel(text(detail, "productModel")).currentSoftwareVersion(text(detail, "currentVersion"))
                    .targetSoftwareVersion(text(detail, "targetVersion")).remark(text(detail, "remark")).sortOrder(order++).build();
            audit(row, command); inserted(devices.insert(row));
        }
        if (overview != null) {
            for (JsonNode value : overview.path("scheduleTable")) {
                var row = CutoverPlanScheduleDO.builder().planRevisionId(plan.getId()).sequenceNo(value.path("sequenceNo").asInt())
                        .planTime(time(value.path("plannedAt").asLong())).content(value.path("content").asText()).build();
                audit(row, command); inserted(schedules.insert(row));
            }
            for (var slot : Map.of("PRE_TOPOLOGY", "preTopologyFile", "POST_TOPOLOGY", "postTopologyFile",
                    "NETWORK_CONFIGURATION", "networkConfigurationFile").entrySet()) {
                var value = overview.get(slot.getValue());
                if (value == null || value.isNull()) continue;
                var fact = JsonUtils.parseObject(value.toString(), CutoverPlanFilePort.FileFact.class);
                var row = CutoverPlanFileDO.builder().planRevisionId(plan.getId()).fileRoleCode(slot.getKey())
                        .fileArtifactId(fact.artifactId()).fileVersionNo(fact.versionNo()).fileReferenceKey(fact.referenceKey())
                        .fileArtifactFactVersion(fact.fileFactVersion().artifactVersion().longValue())
                        .fileReferenceFactVersion(fact.fileFactVersion().referenceVersion().longValue())
                        .fileAvailabilityFactVersion(fact.fileFactVersion().availabilityVersion().longValue())
                        .fileScopeVersion(fact.scopeVersion()).fileSha256(fact.sha256()).build();
                audit(row, command); inserted(files.insert(row));
            }
        }
        Map<Long, String> mitigations = new HashMap<>();
        if (command.content() != null) command.content().path("riskMitigations").forEach(row ->
                mitigations.put(row.path("riskFact").path("checklistItemId").asLong(), row.path("mitigation").asText()));
        order = 0;
        for (var fact : command.source().failedRiskFacts()) {
            var result = checklistResults.selectByVersion(CutoverChecklistResultVersionQuery.builder()
                    .tenantId(plan.getTenantId()).checklistItemId(fact.checklistItemId()).resultVersion(fact.itemResultVersion()).build());
            if (result == null) throw new IllegalArgumentException("方案引用的风险结果版本不存在");
            var item = checklistItems.selectById(result.getChecklistItemId());
            if (item == null || !Objects.equals(item.getTenantId(), plan.getTenantId())
                    || !Objects.equals(item.getChecklistId(), plan.getChecklistId())
                    || !fact.equals(riskFact(result))) throw new IllegalArgumentException("方案风险来源不属于精确清单版本");
            var row = CutoverPlanRiskMitigationDO.builder().planRevisionId(plan.getId()).checklistResultId(result.getId())
                    .checklistItemId(fact.checklistItemId()).checklistResultVersion(fact.itemResultVersion())
                    .mitigation(mitigations.get(fact.checklistItemId())).sortOrder(order++).build();
            audit(row, command); inserted(risks.insert(row));
        }
    }

    public CutoverPlanSourcePort.SourceSnapshot source(CutoverPlanRevisionDO plan) {
        requireTenant(plan.getTenantId());
        var sections = configuration.sectionViews(plan.getConfigurationRevisionId()).stream()
                .filter(row -> (row.getCutoverTypeCodes().isEmpty() || row.getCutoverTypeCodes().contains(plan.getSourceCutoverTypeCode()))
                        && row.getLevelCodes().contains(plan.getGradeCode()))
                .map(row -> new CutoverPlanSourcePort.TemplateSectionSnapshot(row.getStableSectionKey(), row.getTitle(),
                        row.getSortOrder(), row.getCutoverTypeCodes().isEmpty() ? List.of(plan.getSourceCutoverTypeCode()) : row.getCutoverTypeCodes(),
                        row.getLevelCodes(), Boolean.TRUE.equals(row.getRequired()))).toList();
        return new CutoverPlanSourcePort.SourceSnapshot(1, plan.getCutoverTaskId(), plan.getSourceTaskVersion(),
                plan.getAssessmentId(), plan.getAssessmentVersion(), plan.getGradeCode(), plan.getChecklistId(), plan.getChecklistVersion(),
                plan.getSourceProjectId(), Math.toIntExact(plan.getSourceProjectVersion()), plan.getSourceProjectScopeVersion(),
                deviceRows(plan).stream().map(row -> new CutoverPlanSourcePort.DeviceSnapshot(row.getDeviceId(), row.getSn(),
                        row.getProjectAssignmentVersion(), row.getDeviceTypeCode(), row.getDeviceTypeSourceVersion())).toList(),
                plan.getConfigurationRevisionId(), plan.getConfigurationCode(), plan.getConfigurationRevisionNo(), sections,
                riskRows(plan).stream().map(this::riskFact).toList());
    }

    public JsonNode sourceView(CutoverPlanRevisionDO plan) {
        requireTenant(plan.getTenantId());
        if (!"LEGACY_FORWARD".equals(plan.getOriginCode())) return JsonUtils.getObjectMapper().valueToTree(source(plan));
        if (plan.getLegacyPlanId() == null || plan.getLegacyPlanId() <= 0 || plan.getLegacyTaskId() == null || plan.getLegacyTaskId() <= 0
                || plan.getLegacySourceVersion() == null || plan.getLegacySourceVersion() < 0
                || plan.getLegacyStatusRaw() == null || plan.getLegacyStatusRaw() < 0 || plan.getLegacyStatusRaw() > 4
                || !validText(plan.getLegacyCode(), 64) || !validText(plan.getLegacyName(), 128)
                || plan.getLegacyGradeCode() == null || !List.of("A", "B", "C", "D").contains(plan.getLegacyGradeCode())
                || plan.getLegacyRemark() != null && (!plan.getLegacyRemark().equals(plan.getLegacyRemark().trim()) || plan.getLegacyRemark().length() > 4000)
                || !"FCUT004_LEGACY_V1".equals(plan.getLegacyMappingVersion())) {
            throw new IllegalArgumentException("旧方案来源投影不完整");
        }
        ObjectNode row = JsonUtils.getObjectMapper().createObjectNode();
        row.put("sourceTable", "pms_cut_plan_retired"); row.put("sourceId", plan.getLegacyPlanId());
        row.put("sourceTenantId", plan.getTenantId()); row.put("sourceTaskId", plan.getLegacyTaskId());
        row.put("sourceVersion", plan.getLegacySourceVersion()); row.put("sourceStatusRaw", plan.getLegacyStatusRaw());
        row.put("mappingVersion", plan.getLegacyMappingVersion()); row.put("code", plan.getLegacyCode());
        row.put("name", plan.getLegacyName()); row.put("level", plan.getLegacyGradeCode()); row.put("remark", plan.getLegacyRemark());
        return row;
    }

    public ObjectNode content(CutoverPlanRevisionDO plan) {
        requireTenant(plan.getTenantId());
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("editMode", plan.getEditModeCode());
        if (!"ONLINE_TEMPLATE_STANDARD".equals(plan.getEditModeCode())) return root;
        ObjectNode overview = root.putObject("overview");
        overview.put("projectDescription", plan.getProjectDescription());
        var schedule = overview.putArray("scheduleTable");
        schedules.selectListByParents(CutoverPlanScheduleListQuery.builder().tenantId(plan.getTenantId())
                .planRevisionIds(Set.of(plan.getId())).build()).forEach(value -> {
            var row = schedule.addObject(); row.put("sequenceNo", value.getSequenceNo());
            putWireLong(row, "plannedAt", millis(value.getPlanTime())); row.put("content", value.getContent());
        });
        overview.putNull("preTopologyFile"); overview.putNull("postTopologyFile"); overview.putNull("networkConfigurationFile");
        for (var file : files.selectListByParents(CutoverPlanFileListQuery.builder().tenantId(plan.getTenantId())
                .planRevisionIds(Set.of(plan.getId())).build())) {
            String slot = switch (file.getFileRoleCode()) {
                case "PRE_TOPOLOGY" -> "preTopologyFile"; case "POST_TOPOLOGY" -> "postTopologyFile";
                case "NETWORK_CONFIGURATION" -> "networkConfigurationFile"; default -> throw new IllegalArgumentException("未知方案附件槽位");
            };
            overview.set(slot, fileNode(file));
        }
        var summary = overview.putArray("deviceSummary");
        var detailRows = JsonUtils.getObjectMapper().createArrayNode();
        for (var device : deviceRows(plan)) {
            var row = summary.addObject(); putWireLong(row, "deviceId", device.getDeviceId());
            row.put("serialNumber", device.getSn()); putWireLong(row, "projectAssignmentVersion", device.getProjectAssignmentVersion());
            row.put("deviceTypeCode", device.getDeviceTypeCode()); row.put("deviceTypeSourceVersion", device.getDeviceTypeSourceVersion());
            if (device.getProductModel() != null || device.getCurrentSoftwareVersion() != null
                    || device.getTargetSoftwareVersion() != null || device.getRemark() != null) {
                var detail = detailRows.addObject(); putWireLong(detail, "deviceId", device.getDeviceId());
                detail.put("productModel", device.getProductModel()); detail.put("currentVersion", device.getCurrentSoftwareVersion());
                detail.put("targetVersion", device.getTargetSoftwareVersion()); detail.put("remark", device.getRemark());
            }
        }
        if (!detailRows.isEmpty()) overview.set("deviceDetails", detailRows);
        var mitigationRows = root.putArray("riskMitigations");
        for (var value : riskRows(plan)) {
            if (value.getMitigation() == null) continue;
            var row = mitigationRows.addObject();
            ObjectNode fact = JsonUtils.getObjectMapper().valueToTree(riskFact(value));
            putWireLong(fact, "checklistItemId", value.getChecklistItemId());
            row.set("riskFact", fact); row.put("mitigation", value.getMitigation());
        }
        if (plan.getRollbackDescription() != null) root.put("rollbackDescription", plan.getRollbackDescription());
        if (plan.getCustomerContactId() != null) root.set("customerContactReference", JsonUtils.getObjectMapper().valueToTree(
                new CutoverPlanContactPolicy.Selection(plan.getCustomerContactId(), Math.toIntExact(plan.getCustomerContactVersion()))));
        return root;
    }

    public CutoverSupportArrangementDO currentSupport(CutoverSupportArrangementDO baseline) {
        requireTenant(baseline.getTenantId());
        if (baseline.getCurrentSupportContactChangeId() == null) return baseline;
        var change = contactChange(baseline);
        var current = new CutoverSupportArrangementDO(); BeanUtils.copyProperties(baseline, current);
        current.setPersonName(change.getAfterPersonName()); current.setPhone(change.getAfterPhone());
        current.setCustomerContactId(change.getAfterCustomerContactId());
        current.setCustomerContactVersion(change.getAfterCustomerContactVersion());
        current.setArrivalTime(change.getAfterArriveTime()); return current;
    }

    public static ObjectNode supportContent(CutoverSupportArrangementDO value) {
        ObjectNode row = JsonUtils.getObjectMapper().createObjectNode();
        putWireLong(row, "arrangementId", value.getId());
        row.put("roleCode", value.getRoleCode()); row.put("personName", value.getPersonName());
        row.put("dutyDescription", value.getDutyDescription()); row.put("phone", value.getPhone());
        row.put("arrivalTime", value.getArrivalTime() == null ? 0L : millis(value.getArrivalTime()));
        if (value.getCustomerContactId() == null) row.putNull("customerContactReference");
        else {
            var reference = row.putObject("customerContactReference");
            putWireLong(reference, "contactId", value.getCustomerContactId());
            reference.put("contactVersion", Math.toIntExact(value.getCustomerContactVersion()));
        }
        return row;
    }

    public CutoverPlanContactPolicy.Selection currentCustomerContact(CutoverPlanRevisionDO plan) {
        requireTenant(plan.getTenantId());
        var baseline = supports.selectListByPlan(new CutoverPlanChildrenQuery(plan.getTenantId(), plan.getId())).stream()
                .filter(row -> "CUSTOMER".equals(row.getRoleCode())).findFirst().orElse(null);
        if (baseline != null && baseline.getCurrentSupportContactChangeId() != null) {
            var change = contactChange(baseline);
            if (change.getAfterCustomerContactId() == null) return null;
            return new CutoverPlanContactPolicy.Selection(change.getAfterCustomerContactId(), Math.toIntExact(change.getAfterCustomerContactVersion()));
        }
        return plan.getCustomerContactId() == null ? null : new CutoverPlanContactPolicy.Selection(
                plan.getCustomerContactId(), Math.toIntExact(plan.getCustomerContactVersion()));
    }

    public CutoverPlanSupportContactChangeDO contactChange(CutoverSupportArrangementDO baseline) {
        requireTenant(baseline.getTenantId());
        var change = contactChanges.selectById(baseline.getCurrentSupportContactChangeId());
        if (change == null || !Objects.equals(change.getTenantId(), baseline.getTenantId())
                || !Objects.equals(change.getSupportArrangementId(), baseline.getId())) throw new IllegalArgumentException("保障联系人变更引用损坏");
        return change;
    }

    private List<CutoverPlanDeviceDO> deviceRows(CutoverPlanRevisionDO plan) {
        return devices.selectListByParents(CutoverPlanDeviceListQuery.builder().tenantId(plan.getTenantId())
                .planRevisionIds(Set.of(plan.getId())).build());
    }
    private List<CutoverPlanRiskMitigationDO> riskRows(CutoverPlanRevisionDO plan) {
        return risks.selectListByParents(CutoverPlanRiskMitigationListQuery.builder().tenantId(plan.getTenantId())
                .planRevisionIds(Set.of(plan.getId())).build());
    }
    private CutoverPlanSourcePort.RiskFactSnapshot riskFact(CutoverPlanRiskMitigationDO reference) {
        var result = checklistResults.selectById(reference.getChecklistResultId());
        if (result == null || !Objects.equals(result.getTenantId(), reference.getTenantId())
                || !Objects.equals(result.getChecklistItemId(), reference.getChecklistItemId())
                || !Objects.equals(result.getResultVersion(), reference.getChecklistResultVersion())) throw new IllegalArgumentException("方案风险结果引用损坏");
        return riskFact(result);
    }
    private CutoverPlanSourcePort.RiskFactSnapshot riskFact(CutoverChecklistItemResultDO result) {
        var item = checklistItems.selectById(result.getChecklistItemId());
        if (item == null || !Objects.equals(item.getTenantId(), result.getTenantId())) throw new IllegalArgumentException("方案风险采集项引用损坏");
        var feedback = resultStorage.feedback(result);
        String description = result.getFactDescription();
        if (description == null || description.isBlank()) description = feedback == null ? null : feedback.getRemark();
        if (description == null || description.isBlank()) description = resultStorage.answer(new CutoverChecklistResultStore.Read(item, result));
        return new CutoverPlanSourcePort.RiskFactSnapshot(item.getId(), item.getStableItemKey(), result.getResultVersion(),
                item.getItemName(), "NOT_PASSED", description.length() > 4000 ? description.substring(0, 4000) : description);
    }
    private static ObjectNode fileNode(CutoverPlanFileDO value) {
        ObjectNode row = JsonUtils.getObjectMapper().createObjectNode(); putWireLong(row, "artifactId", value.getFileArtifactId());
        row.put("versionNo", value.getFileVersionNo()); row.put("referenceKey", value.getFileReferenceKey());
        row.set("fileFactVersion", JsonUtils.getObjectMapper().valueToTree(new CutoverPlanFilePort.FileFactVersion(
                Math.toIntExact(value.getFileArtifactFactVersion()), Math.toIntExact(value.getFileReferenceFactVersion()),
                Math.toIntExact(value.getFileAvailabilityFactVersion()))));
        putWireLong(row, "scopeVersion", value.getFileScopeVersion()); row.put("sha256", value.getFileSha256()); return row;
    }
    private static void audit(TenantBaseDO row, Write command) {
        row.setTenantId(command.plan().getTenantId()); row.setCreator(String.valueOf(command.actorId()));
        row.setUpdater(String.valueOf(command.actorId())); row.setCreateTime(command.time()); row.setUpdateTime(command.time()); row.setDeleted(false);
    }
    private static void requireTenant(Long tenant) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(tenant, TenantContextHolder.getTenantId())) throw new IllegalArgumentException("方案租户上下文不一致");
    }
    private static String text(JsonNode node, String field) { return node != null && node.hasNonNull(field) ? node.get(field).asText() : null; }
    private static boolean validText(String value, int max) { return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= max; }
    private static void inserted(int count) { if (count != 1) throw new IllegalStateException("方案明细未完整保存"); }
    private static LocalDateTime time(long value) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()); }
    private static long millis(LocalDateTime value) { return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private static void putWireLong(ObjectNode node, String field, long value) {
        if (value > -9_007_199_254_740_991L && value < 9_007_199_254_740_991L) node.put(field, value); else node.put(field, Long.toString(value));
    }
}
