package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.SITE_SURVEY_FORM_INVALID;

final class SiteSurveyEntityBusinessValues {
    private static final Set<String> BOOLEANS = Set.of("cabinetReady", "cableReady", "moduleReady",
            "originalModule", "manufacturerInstallation", "railTrayRequired", "materialMatches");
    private static final Map<String, Set<String>> CHOICES = Map.of(
            "powerTypes", Set.of("AC", "DC", "MULTIPLE"),
            "powerEnvironments", Set.of("CN", "EU", "OTHER"),
            "networkPortTypes", Set.of("GE_COPPER", "GE_FIBER", "10GE_FIBER", "40GE_FIBER"));
    static void validate(Map<String, Object> values, Long projectId) {
        Object endDate = values.get("requiredEndDate");
        if (endDate != null && !"".equals(endDate)) {
            if (!(endDate instanceof String date)) throw exception(SITE_SURVEY_FORM_INVALID);
            try { java.time.LocalDate.parse(date); }
            catch (java.time.format.DateTimeParseException invalid) { throw exception(SITE_SURVEY_FORM_INVALID); }
        }
        for (String key : BOOLEANS) {
            Object value = values.get(key);
            if (value != null && !"".equals(value) && !(value instanceof Boolean)) throw exception(SITE_SURVEY_FORM_INVALID);
        }
        CHOICES.forEach((key, options) -> {
            Object value = values.get(key);
            if (value != null && (!(value instanceof List<?> items) || items.stream().anyMatch(item -> !options.contains(item)))) {
                throw exception(SITE_SURVEY_FORM_INVALID);
            }
        });
        Object materials = values.get("selectedMaterials");
        if (materials != null && !"".equals(materials)) {
            if (!(materials instanceof List<?> items)) throw exception(SITE_SURVEY_FORM_INVALID);
            Set<String> serials = new HashSet<>();
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> row) || !(row.get("sn") instanceof String sn) || sn.isBlank()
                        || !serials.add(sn) || !String.valueOf(projectId).equals(String.valueOf(row.get("projectId")))) {
                    throw exception(SITE_SURVEY_FORM_INVALID);
                }
            }
        }
    }
}
