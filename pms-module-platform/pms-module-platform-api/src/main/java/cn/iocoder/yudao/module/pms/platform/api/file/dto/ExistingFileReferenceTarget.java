package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record ExistingFileReferenceTarget(
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey,
        Long expectedScopeVersion) {

    public ExistingFileReferenceTarget {
        ownerContext = FileActionCodes.requireText(ownerContext, "ownerContext");
        objectType = FileActionCodes.requireText(objectType, "objectType");
        objectId = FileActionCodes.requireText(objectId, "objectId");
        purposeCode = FileActionCodes.requireText(purposeCode, "purposeCode");
        referenceKey = FileActionCodes.requireText(referenceKey, "referenceKey");
        if (expectedScopeVersion == null || expectedScopeVersion < 0) {
            throw new IllegalArgumentException("invalid target file policy version");
        }
        boolean requirementSection = "SOL".equals(ownerContext)
                && "REQUIREMENT_ANALYSIS_SECTION".equals(objectType)
                && "SECTION_ATTACHMENT".equals(purposeCode);
        boolean dynamicFormField = "PLATFORM".equals(ownerContext)
                && "DYNAMIC_FORM_INSTANCE".equals(objectType)
                && purposeCode.startsWith("FORM_FIELD_ATTACHMENT/")
                && purposeCode.length() > "FORM_FIELD_ATTACHMENT/".length();
        if ((!requirementSection && !dynamicFormField) || !objectId.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("unsupported existing file attachment target");
        }
        try {
            if (!java.util.UUID.fromString(referenceKey).toString().equalsIgnoreCase(referenceKey)) {
                throw new IllegalArgumentException("target reference key must be a UUID");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("target reference key must be a UUID", ex);
        }
    }
}
