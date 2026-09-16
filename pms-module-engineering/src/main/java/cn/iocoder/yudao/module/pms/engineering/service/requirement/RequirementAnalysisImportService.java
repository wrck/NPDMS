package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** A whole legacy project object, its revisions, definitions and file references migrate atomically. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisImportService {
    private final RequirementAnalysisImportMapper importer;
    private final RequirementAnalysisMapper mapper;
    private final EntityCapabilityImportApi capabilities;

    @Transactional(rollbackFor = Exception.class)
    public Result importProject(Long projectId, EntityActor actor) {
        var query = new RequirementProjectQuery(actor.tenantId(), projectId);
        var sources = importer.lockSources(query);
        if (sources.isEmpty()) throw invalid(projectId, "No legacy requirement analysis");
        validateSources(projectId, sources);
        // Verify original platform ownership and operator authority before writing business targets.
        var schemas = new LinkedHashMap<Long, EntityCapabilityImportApi.SourceForm>();
        sources.forEach(source -> schemas.put(source.getId(), capabilities.inspect(source(source), actor)));
        var existing = importer.lockTargets(query);
        var current = importer.lockCurrent(query);
        Long entityId = existing.isEmpty() ? current == null ? IdWorker.getId() : current.getId() : existing.getFirst().getEntityId();
        var sourceIds = sources.stream().map(RequirementAnalysisImportSource::getId).collect(Collectors.toSet());
        if (existing.stream().anyMatch(row -> !entityId.equals(row.getEntityId()) || !sourceIds.contains(row.getId()))
                || current != null && !entityId.equals(current.getId())) throw invalid(projectId, "Target has unrelated business identity or revisions");
        var effective = sources.stream().filter(row -> Integer.valueOf(1).equals(row.getEffectiveMarker())).findFirst().orElse(null);
        Map<Long, RequirementAnalysisRevisionDO> expected = new LinkedHashMap<>();
        var byId = existing.stream().collect(Collectors.toMap(RequirementAnalysisRevisionDO::getId, row -> row));
        for (var source : sources) {
            var row = convert(source, entityId);
            if ("DRAFT".equals(row.getRevisionState()) && effective != null) {
                // The old schema had no current-row concurrency token. Adopt the imported current row
                // as the cutover baseline for the existing draft; do not invent historical tokens.
                row.setBaseEffectiveRevisionId(effective.getId());
                row.setBaseEntityVersion(1);
            }
            expected.put(row.getId(), row);
            var target = byId.get(row.getId());
            if (target == null) mapper.insertRevision(row);
            else requireEqual(projectId, row, target);
            importCapabilities(EntityDataRef.revision(row.revisionRef()), source, schemas.get(row.getId()), actor);
            capabilities.importFiles(row.revisionRef(), source(source), "REQUIREMENT_ANALYSIS_REVISION", actor);
        }
        if (effective == null) {
            if (current != null) throw invalid(projectId, "Unexpected current business row without an effective revision");
        } else {
            var expectedCurrent = BeanUtils.toBean(expected.get(effective.getId()), RequirementAnalysisDO.class);
            expectedCurrent.setId(entityId);
            expectedCurrent.setVersion(1);
            if (current == null) mapper.insertCurrent(expectedCurrent);
            else requireEqual(projectId, expectedCurrent, current);
            importCapabilities(EntityDataRef.current(expected.get(effective.getId()).entityRef()), effective,
                    schemas.get(effective.getId()), actor);
            requireEqual(projectId, expectedCurrent, importer.lockCurrent(query));
        }
        for (var stored : importer.lockTargets(query)) requireEqual(projectId, expected.get(stored.getId()), stored);
        return new Result(entityId, projectId, sources.size(), existing.isEmpty());
    }

    private void importCapabilities(EntityDataRef target, RequirementAnalysisImportSource source,
                                    EntityCapabilityImportApi.SourceForm schema, EntityActor actor) {
        var fixedCodes = RequirementAnalysisEntityProvider.FIELDS.fields().stream().map(EntityField::code).collect(Collectors.toSet());
        Map<String, String> bindings = new LinkedHashMap<>();
        schema.fields().stream().filter(field -> !field.controlledFile()).forEach(field -> {
            String property = propertyCode(field.fieldKey());
            if (fixedCodes.contains(property)) bindings.put(field.fieldKey(), property);
        });
        Map<String, Object> extras = new LinkedHashMap<>();
        values(source).forEach((code, value) -> { if (!fixedCodes.contains(propertyCode(code))) extras.put(code, value); });
        capabilities.importContent(new EntityCapabilityImportApi.Import(target, source(source), bindings, extras, actor));
    }

    static RequirementAnalysisRevisionDO convert(RequirementAnalysisImportSource source, Long entityId) {
        var row = BeanUtils.toBean(source, RequirementAnalysisRevisionDO.class);
        row.setEntityId(entityId);
        row.setRevisionState("COMPLETED".equals(source.getStatusCode()) ? "FROZEN" : "DRAFT");
        var codes = RequirementAnalysisEntityProvider.FIELDS.fields().stream().map(EntityField::code).collect(Collectors.toSet());
        Map<String, Object> fixed = new LinkedHashMap<>();
        values(source).forEach((key, value) -> {
            String property = propertyCode(key);
            if (codes.contains(property)) fixed.put(property, value);
        });
        RequirementAnalysisEntityProvider.FIELDS.write(row, fixed);
        return row;
    }

    static void validateSources(Long projectId, List<RequirementAnalysisImportSource> sources) {
        Set<Integer> numbers = new HashSet<>();
        var ids = sources.stream().map(RequirementAnalysisImportSource::getId).collect(Collectors.toSet());
        int drafts = 0;
        int effective = 0;
        for (var source : sources) {
            if (!projectId.equals(source.getProjectId()) || source.getRevisionNo() == null || source.getRevisionNo() <= 0
                    || !numbers.add(source.getRevisionNo())) throw invalid(projectId, "Invalid or conflicting revision number at " + source.getId());
            if (source.getSourceRevisionId() != null && (!ids.contains(source.getSourceRevisionId())
                    || source.getSourceRevisionId().equals(source.getId()))) throw invalid(projectId, "Missing or invalid source at " + source.getId());
            if (source.getDynamicFormInstanceId() == null || source.getEntityValueJson() == null
                    || source.getVersion() == null || source.getVersion() < 1) throw invalid(projectId, "Missing legacy content or form identity at " + source.getId());
            if ("DRAFT".equals(source.getStatusCode())) {
                drafts++;
                if (!Integer.valueOf(1).equals(source.getDraftMarker()) || source.getEffectiveMarker() != null
                        || source.getFrozenAt() != null || source.getFrozenBy() != null) throw invalid(projectId, "Invalid draft evidence at " + source.getId());
            } else if ("COMPLETED".equals(source.getStatusCode())) {
                if (source.getDraftMarker() != null || source.getFrozenAt() == null || source.getFrozenBy() == null)
                    throw invalid(projectId, "Missing completion evidence at " + source.getId());
                if (Integer.valueOf(1).equals(source.getEffectiveMarker())) effective++;
            } else throw invalid(projectId, "Unsupported legacy state at " + source.getId());
        }
        if (drafts > 1 || effective > 1) throw invalid(projectId, "Conflicting draft or effective revisions");
    }

    private static EntityCapabilityImportApi.Source source(RequirementAnalysisImportSource row) {
        return new EntityCapabilityImportApi.Source(new EntityRef(row.getTenantId(), "SOL", "REQUIREMENT_ANALYSIS", row.getId()),
                row.getDynamicFormInstanceId(), null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(RequirementAnalysisImportSource row) {
        var values = JsonUtils.parseObject(row.getEntityValueJson(), Map.class);
        if (values == null) throw invalid(row.getProjectId(), "Missing legacy values at " + row.getId());
        return values;
    }

    private static String propertyCode(String code) { return StrUtil.toCamelCase(code.toLowerCase(Locale.ROOT)); }

    private static void requireEqual(Long projectId, Object expected, Object actual) {
        if (expected == null || actual == null) throw invalid(projectId, "Missing target content");
        var left = BeanUtil.beanToMap(expected);
        var right = BeanUtil.beanToMap(actual);
        var differences = left.keySet().stream().filter(key -> !Objects.equals(left.get(key), right.get(key))).sorted().toList();
        if (!differences.isEmpty()) throw invalid(projectId, "Content differs at " + left.get("id") + ": " + differences);
    }

    private static IllegalStateException invalid(Long projectId, String reason) {
        return new IllegalStateException("Requirement analysis project " + projectId + ": " + reason);
    }

    public record Result(Long entityId, Long projectId, int revisionCount, boolean created) {}
}
