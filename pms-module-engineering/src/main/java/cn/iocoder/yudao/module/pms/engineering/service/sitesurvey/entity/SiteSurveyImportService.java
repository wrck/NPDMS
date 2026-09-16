package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.hutool.core.bean.BeanUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyImportSource;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyImportQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** One legacy object per transaction; existing target content is only compared, never overwritten. */
@Service
@RequiredArgsConstructor
public class SiteSurveyImportService {
    private final SiteSurveyImportMapper legacy;
    private final SiteSurveyEntityMapper mapper;
    private final SiteSurveyDetails details;
    private final EntityCapabilityImportApi capabilities;

    @Transactional(rollbackFor = Exception.class)
    public Result importOne(Long surveyId, EntityActor actor) {
        var source = legacy.lockSource(new SiteSurveyImportQuery(actor.tenantId(), surveyId));
        if (source == null) throw new IllegalStateException("Missing legacy survey: " + surveyId);
        var ref = new EntityRef(actor.tenantId(), "SOL", "SITE_SURVEY", surveyId);
        var formSource = new EntityCapabilityImportApi.Source(ref, null, source.getFormRevisionId());
        // Public import API verifies the operator and original platform-owned form before any target write.
        var form = capabilities.inspect(formSource, actor);
        var expected = convert(source);
        var current = legacy.lockTarget(new SiteSurveyImportQuery(actor.tenantId(), surveyId));
        boolean created = current == null;
        if (created) {
            mapper.insert(expected);
            details.save(expected, actor.userId());
        } else {
            details.load(current);
            var differences = differences(expected, current);
            if (!differences.isEmpty()) throw new IllegalStateException("Survey " + surveyId + " differs: " + differences);
        }
        Set<String> fixedCodes = SiteSurveyEntityProvider.FIELDS.fields().stream().map(EntityField::code).collect(Collectors.toSet());
        Map<String, String> fixedBindings = new LinkedHashMap<>();
        form.fields().stream().filter(field -> !field.controlledFile()).forEach(field -> {
            String property = propertyCode(field.fieldKey());
            if (fixedCodes.contains(property)) fixedBindings.put(field.fieldKey(), property);
        });
        Map<String, Object> extras = new LinkedHashMap<>();
        if (source.getFormExtraValues() != null) source.getFormExtraValues().forEach((key, value) -> {
            if (!fixedCodes.contains(propertyCode(key))) extras.put(key, value);
        });
        capabilities.importContent(new EntityCapabilityImportApi.Import(EntityDataRef.current(ref), formSource,
                fixedBindings, extras, actor));
        var stored = legacy.lockTarget(new SiteSurveyImportQuery(actor.tenantId(), surveyId));
        details.load(stored);
        var differences = differences(expected, stored);
        if (!differences.isEmpty()) throw new IllegalStateException("Survey " + surveyId + " reconciliation failed: " + differences);
        return new Result(surveyId, source.getProjectId(), created);
    }

    static SiteSurveyEntityDO convert(SiteSurveyImportSource source) {
        var target = BeanUtils.toBean(source, SiteSurveyEntityDO.class);
        var fields = SiteSurveyEntityProvider.FIELDS.fields().stream().collect(Collectors.toMap(EntityField::code, field -> field));
        Map<String, Object> patch = new LinkedHashMap<>();
        if (source.getFormExtraValues() != null) source.getFormExtraValues().forEach((key, value) -> {
            var field = fields.get(propertyCode(key));
            if (field != null) patch.put(field.code(), "".equals(value) && field.type() != EntityField.Type.TEXT ? null : value);
        });
        SiteSurveyEntityBusinessValues.validate(patch, source.getProjectId());
        SiteSurveyEntityProvider.FIELDS.write(target, patch);
        // Missing legacy multi-select values and empty lists both mean no child rows.
        Map<String, Object> emptyLists = new LinkedHashMap<>();
        SiteSurveyEntityProvider.FIELDS.read(target).forEach((code, value) -> {
            var type = fields.get(code).type();
            if (value == null && (type == EntityField.Type.TEXT_LIST || type == EntityField.Type.OBJECT_LIST)) {
                emptyLists.put(code, List.of());
            }
        });
        SiteSurveyEntityProvider.FIELDS.write(target, emptyLists);
        return target;
    }

    static List<String> differences(SiteSurveyEntityDO expected, SiteSurveyEntityDO actual) {
        Map<String, Object> left = BeanUtil.beanToMap(expected);
        Map<String, Object> right = BeanUtil.beanToMap(actual);
        // Child persistence metadata is new; compare every business property of every ordered child.
        left.putAll(SiteSurveyEntityProvider.FIELDS.read(expected));
        right.putAll(SiteSurveyEntityProvider.FIELDS.read(actual));
        return left.keySet().stream().filter(key -> !Objects.equals(left.get(key), right.get(key))).sorted().toList();
    }

    private static String propertyCode(String fieldKey) {
        return fieldKey.startsWith("extra_") ? fieldKey.substring("extra_".length()) : fieldKey;
    }

    public record Result(Long entityId, Long projectId, boolean created) {}
}
