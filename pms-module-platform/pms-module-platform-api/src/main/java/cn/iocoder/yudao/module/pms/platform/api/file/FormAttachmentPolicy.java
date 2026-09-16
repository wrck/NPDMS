package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import java.util.Set;

/** Existing form attachment limits shared by instance and entity-bound form attachments. */
public final class FormAttachmentPolicy {
    private FormAttachmentPolicy() {}
    public static final String PURPOSE_PREFIX = "FORM_FIELD_ATTACHMENT/";
    public static final String CATEGORY = "DYNAMIC_FORM_ATTACHMENT";
    public static final long MAX_SIZE = 52_428_800L;
    public static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "text/plain", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    public static FileBusinessObjectPolicyFact fact(boolean allowed, Long scopeVersion, boolean frozen) {
        return new FileBusinessObjectPolicyFact(allowed, scopeVersion, frozen ? "IMMUTABLE" : "MUTABLE", "MULTIPLE",
                Set.of(CATEGORY), MEDIA_TYPES, MAX_SIZE, "INTERNAL");
    }
}
