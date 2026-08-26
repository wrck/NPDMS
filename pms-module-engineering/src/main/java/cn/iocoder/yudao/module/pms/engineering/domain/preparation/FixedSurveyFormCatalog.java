package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import java.util.List;

public record FixedSurveyFormCatalog(Integer schemaVersion, String catalogCode, Integer catalogVersion,
                                     List<FieldDefinition> commonFields, List<FormDefinition> forms) {

    public record FieldDefinition(String fieldCode, String fieldType, Boolean required,
                                  Integer maxLength, List<String> options, Integer sortOrder) {
    }

    public record FormDefinition(String formCode, Integer formVersion) {
    }
}
